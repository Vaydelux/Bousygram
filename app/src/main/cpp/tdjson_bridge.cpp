#include <jni.h>
#include <dlfcn.h>
#include <mutex>
#include <string>

namespace {
using Create = int (*)();
using Send = void (*)(int, const char *);
using Receive = const char *(*)(double);
using Execute = const char *(*)(const char *);
Create create_ptr = nullptr;
Send send_ptr = nullptr;
Receive receive_ptr = nullptr;
Execute execute_ptr = nullptr;
void *handle = nullptr;
std::once_flag once;
std::string issue;
std::mutex result_guard;
void load() {
    handle = dlopen("libtdjson.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) { issue = "Missing libtdjson.so or unresolved native dependency"; return; }
    create_ptr = reinterpret_cast<Create>(dlsym(handle, "td_create_client_id"));
    send_ptr = reinterpret_cast<Send>(dlsym(handle, "td_send"));
    receive_ptr = reinterpret_cast<Receive>(dlsym(handle, "td_receive"));
    execute_ptr = reinterpret_cast<Execute>(dlsym(handle, "td_execute"));
    if (!create_ptr || !send_ptr || !receive_ptr || !execute_ptr)
        issue = "Missing mandatory TDLib JSON export";
}
bool available() { std::call_once(once, load); return issue.empty(); }
void fail(JNIEnv *env, const char *cls, const char *msg) {
    jclass klass = env->FindClass(cls);
    if (klass) { env->ThrowNew(klass, msg); env->DeleteLocalRef(klass); }
}
bool require(JNIEnv *env) {
    if (available()) return true;
    fail(env,"java/lang/UnsatisfiedLinkError",issue.c_str());
    return false;
}
// Android's JNI modified UTF-8 is not standard UTF-8. Convert through UTF-16.
std::string encode(JNIEnv *env, jstring input) {
    if (!input) { fail(env,"java/lang/IllegalArgumentException","Null TDLib JSON"); return {}; }
    const jsize count = env->GetStringLength(input);
    const jchar *chars = env->GetStringChars(input, nullptr);
    if (!chars) return {};
    std::string out;
    for (jsize i=0;i<count;i++) {
        unsigned cp=chars[i];
        if(cp>=0xD800 && cp<=0xDBFF) {
            if(i+1>=count || chars[i+1]<0xDC00 || chars[i+1]>0xDFFF) {
                env->ReleaseStringChars(input,chars);
                fail(env,"java/lang/IllegalArgumentException","Invalid UTF-16 surrogate");
                return {};
            }
            cp=0x10000+((cp-0xD800)<<10)+(chars[++i]-0xDC00);
        } else if(cp>=0xDC00 && cp<=0xDFFF) {
            env->ReleaseStringChars(input,chars);
            fail(env,"java/lang/IllegalArgumentException","Invalid UTF-16 surrogate");
            return {};
        }
        if(cp<0x80) out.push_back((char)cp);
        else if(cp<0x800) {out.push_back((char)(0xC0|(cp>>6)));out.push_back((char)(0x80|(cp&63)));}
        else if(cp<0x10000) {
            out.push_back((char)(0xE0|(cp>>12)));out.push_back((char)(0x80|((cp>>6)&63)));out.push_back((char)(0x80|(cp&63)));
        } else {
            out.push_back((char)(0xF0|(cp>>18)));out.push_back((char)(0x80|((cp>>12)&63)));
            out.push_back((char)(0x80|((cp>>6)&63)));out.push_back((char)(0x80|(cp&63)));
        }
    }
    env->ReleaseStringChars(input,chars);
    return out;
}
jstring decode(JNIEnv *env, const std::string &str) {
    std::u16string out;
    const unsigned char *p=(const unsigned char *)str.data();
    for(size_t i=0;i<str.size();) {
        unsigned cp=0; int n=0; unsigned b=p[i++];
        if(b<0x80) cp=b;
        else if(b>=0xC2 && b<=0xDF) { cp=b&31; n=1; }
        else if(b>=0xE0 && b<=0xEF) { cp=b&15; n=2; }
        else if(b>=0xF0 && b<=0xF4) { cp=b&7; n=3; }
        else { fail(env,"java/lang/IllegalArgumentException","Invalid UTF-8");return nullptr; }
        for(int j=0;j<n;j++) {
            if(i>=str.size() || (p[i]&0xC0)!=0x80) {fail(env,"java/lang/IllegalArgumentException","Invalid UTF-8");return nullptr;}
            cp=(cp<<6)|(p[i++]&63);
        }
        if((n==1 && cp<0x80)||(n==2 && cp<0x800)||(n==3 && cp<0x10000)||cp>0x10FFFF||(cp>=0xD800 && cp<=0xDFFF)) {
            fail(env,"java/lang/IllegalArgumentException","Invalid UTF-8 codepoint"); return nullptr;
        }
        if(cp<=0xFFFF) out.push_back((char16_t)cp);
        else {cp-=0x10000;out.push_back((char16_t)(0xD800+(cp>>10)));out.push_back((char16_t)(0xDC00+(cp&1023)));}
    }
    return env->NewString((const jchar *)out.data(),(jsize)out.size());
}
}
extern "C" JNIEXPORT jstring JNICALL Java_org_drinkless_tdlib_TdJsonNative_diagnostics(JNIEnv *e,jclass) {
    return decode(e,available() ? std::string("") : issue);
}
extern "C" JNIEXPORT jint JNICALL Java_org_drinkless_tdlib_TdJsonNative_create(JNIEnv *e,jclass) {
    if(!require(e)) return 0;
    return (jint)create_ptr();
}
extern "C" JNIEXPORT void JNICALL Java_org_drinkless_tdlib_TdJsonNative_send(JNIEnv *e,jclass,jint id,jstring j) {
    if(!require(e)) return;
    if(id<=0) {fail(e,"java/lang/IllegalArgumentException","Invalid client ID");return;}
    std::string data=encode(e,j);
    if(!e->ExceptionCheck()) send_ptr(id,data.c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_org_drinkless_tdlib_TdJsonNative_receive(JNIEnv *e,jclass,jdouble timeout) {
    if(!require(e)) return nullptr;
    if(timeout<0 || timeout>10) {fail(e,"java/lang/IllegalArgumentException","Invalid receive timeout");return nullptr;}
    std::string out;
    {
        std::lock_guard<std::mutex> lock(result_guard);
        const char *ptr=receive_ptr(timeout);
        if(!ptr) return nullptr;
        out=ptr; // TDLib owns this buffer, copy before returning.
    }
    return decode(e,out);
}
extern "C" JNIEXPORT jstring JNICALL Java_org_drinkless_tdlib_TdJsonNative_execute(JNIEnv *e,jclass,jstring j) {
    if(!require(e)) return nullptr;
    std::string data=encode(e,j);
    if(e->ExceptionCheck()) return nullptr;
    std::string out;
    {
        std::lock_guard<std::mutex> lock(result_guard);
        const char *ptr=execute_ptr(data.c_str());
        if(!ptr) return nullptr;
        out=ptr;
    }
    return decode(e,out);
}