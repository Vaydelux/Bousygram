/* Isolated qualification bridge; not linked or packaged by the Android app. */
#include <jni.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/time.h>
#include <libavutil/error.h>

#define IO_BYTES (32 * 1024)
#define INPUT_MAX (16LL * 1024 * 1024)
#define OUTPUT_MAX (20LL * 1024 * 1024)
#define PACKET_MAX (2 * 1024 * 1024)
#define PACKETS_MAX 20000
#define WALL_US (60LL * 1000000)
#define TIMELINE_MAX_US (120LL * 1000000)

enum { OK = 0, BAD_INPUT = 1, IO_FAILED = 2, MEDIA_FAILED = 3, LIMIT = 4, CANCELLED = 5, UNSUPPORTED = 6 };

typedef struct {
    JNIEnv *env;
    jobject callbacks;
    jmethodID read, write, active;
    jbyteArray in_array, out_array;
    int64_t size, offset, written, deadline;
    int status;
} Session;

static int fail(Session *s, int code) {
    if (!s->status) s->status = code;
    return AVERROR(EIO);
}

static int alive(Session *s) {
    if (s->status || (*s->env)->ExceptionCheck(s->env)) return 0;
    if (av_gettime_relative() >= s->deadline) { fail(s, LIMIT); return 0; }
    jboolean active = (*s->env)->CallBooleanMethod(s->env, s->callbacks, s->active);
    if ((*s->env)->ExceptionCheck(s->env) || !active) { fail(s, CANCELLED); return 0; }
    return 1;
}
static int interrupt(void *opaque) { return !alive(opaque); }

static int read_packet(void *opaque, uint8_t *buffer, int length) {
    Session *s = opaque;
    if (!alive(s)) return AVERROR_EXIT;
    if (s->offset == s->size) return AVERROR_EOF;
    int count = length > IO_BYTES ? IO_BYTES : length;
    if (count <= 0) return fail(s, IO_FAILED);
    if (count > s->size - s->offset) count = (int)(s->size - s->offset);
    jint n = (*s->env)->CallIntMethod(s->env, s->callbacks, s->read,
                                     (jlong)s->offset, s->in_array, (jint)count);
    if ((*s->env)->ExceptionCheck(s->env)) return fail(s, IO_FAILED);
    /* A missing or short-to-zero source is an error, never fake EOF. */
    if (n <= 0 || n > count) return fail(s, IO_FAILED);
    if (!alive(s)) return AVERROR_EXIT;
    (*s->env)->GetByteArrayRegion(s->env, s->in_array, 0, n, (jbyte *)buffer);
    if ((*s->env)->ExceptionCheck(s->env)) return fail(s, IO_FAILED);
    s->offset += n;
    return n;
}

static int write_packet(void *opaque, const uint8_t *buffer, int length) {
    Session *s = opaque;
    if (length <= 0 || length > OUTPUT_MAX - s->written) return fail(s, LIMIT);
    for (int pos = 0; pos < length;) {
        if (!alive(s)) return AVERROR_EXIT;
        int n = length - pos > IO_BYTES ? IO_BYTES : length - pos;
        (*s->env)->SetByteArrayRegion(s->env, s->out_array, 0, n, (const jbyte *)(buffer + pos));
        if ((*s->env)->ExceptionCheck(s->env)) return fail(s, IO_FAILED);
        jboolean accepted = (*s->env)->CallBooleanMethod(s->env, s->callbacks, s->write, s->out_array, (jint)n);
        if ((*s->env)->ExceptionCheck(s->env) || !accepted) return fail(s, IO_FAILED);
        if (!alive(s)) return AVERROR_EXIT;
        pos += n;
        s->written += n;
    }
    return length;
}

/* No nested URL, external attachment or protocol is available to this bridge. */
static int deny_open(AVFormatContext *ctx, AVIOContext **pb, const char *url, int flags, AVDictionary **options) {
    (void)ctx; (void)pb; (void)url; (void)flags; (void)options;
    return AVERROR(EACCES);
}

static AVIOContext *new_io(Session *s, int output) {
    uint8_t *buffer = av_malloc(IO_BYTES);
    if (!buffer) return NULL;
    AVIOContext *io = avio_alloc_context(buffer, IO_BYTES, output, s,
                    output ? NULL : read_packet, output ? write_packet : NULL, NULL);
    if (!io) av_free(buffer);
    return io;
}
static void free_io(AVIOContext **io) {
    if (*io) { av_freep(&(*io)->buffer); avio_context_free(io); }
}

static int remux(Session *s, int kind) {
    int result = MEDIA_FAILED, opened = 0, video = -1, audio = -1, packets = 0;
    int64_t first[2] = {AV_NOPTS_VALUE, AV_NOPTS_VALUE};
    int64_t last[2] = {AV_NOPTS_VALUE, AV_NOPTS_VALUE};
    int counts[2] = {0, 0};
    AVFormatContext *in = NULL, *out = NULL;
    AVIOContext *reader = NULL, *writer = NULL;
    AVPacket *packet = NULL;
    AVDictionary *options = NULL;
    const AVInputFormat *format = av_find_input_format(kind == 1 ? "matroska" : "mpegts");
    if (!format) return UNSUPPORTED;
    if (!alive(s)) return s->status;
    in = avformat_alloc_context();
    reader = new_io(s, 0);
    if (!in || !reader) goto done;
    in->pb = reader;
    in->flags |= AVFMT_FLAG_CUSTOM_IO;
    in->io_open = deny_open;
    in->interrupt_callback = (AVIOInterruptCB){interrupt, s};
    in->probesize = 2 * 1024 * 1024;
    in->max_analyze_duration = 2 * 1000000;
    in->max_probe_packets = 1024;
    in->max_streams = 2;
    if (avformat_open_input(&in, NULL, format, NULL) < 0) goto done;
    opened = 1;
    if (avformat_find_stream_info(in, NULL) < 0 || !alive(s)) goto done;
    if (in->nb_streams != 2) { result = UNSUPPORTED; goto done; }
    for (unsigned i = 0; i < in->nb_streams; i++) {
        AVCodecParameters *par = in->streams[i]->codecpar;
        if (par->codec_type == AVMEDIA_TYPE_VIDEO && par->codec_id == AV_CODEC_ID_H264 && video == -1) {
            if (par->width <= 0 || par->height <= 0 || par->width > 4096 || par->height > 2160) {
                result = UNSUPPORTED; goto done;
            }
            video = (int)i;
        } else if (par->codec_type == AVMEDIA_TYPE_AUDIO && par->codec_id == AV_CODEC_ID_AAC && audio == -1) {
            if (par->ch_layout.nb_channels < 1 || par->ch_layout.nb_channels > 2) { result = UNSUPPORTED; goto done; }
            audio = (int)i;
        } else { result = UNSUPPORTED; goto done; }
        if (par->extradata_size > 256 * 1024) { result = LIMIT; goto done; }
    }
    if (video < 0 || audio < 0) { result = UNSUPPORTED; goto done; }
    if (avformat_alloc_output_context2(&out, NULL, "mp4", NULL) < 0 || !out) goto done;
    writer = new_io(s, 1);
    if (!writer) goto done;
    out->pb = writer;
    out->flags |= AVFMT_FLAG_CUSTOM_IO | AVFMT_FLAG_AUTO_BSF;
    out->io_open = deny_open;
    out->interrupt_callback = (AVIOInterruptCB){interrupt, s};
    out->max_interleave_delta = 1000000;
    for (unsigned i = 0; i < in->nb_streams; i++) {
        AVStream *track = avformat_new_stream(out, NULL);
        if (!track || avcodec_parameters_copy(track->codecpar, in->streams[i]->codecpar) < 0) goto done;
        track->codecpar->codec_tag = 0;
        track->time_base = in->streams[i]->time_base;
    }
    if (av_dict_set(&options, "movflags", "+delay_moov+empty_moov+default_base_moof+frag_keyframe+skip_trailer", 0) < 0 ||
        av_dict_set(&options, "min_frag_duration", "4000000", 0) < 0 ||
        avformat_write_header(out, &options) < 0 || av_dict_count(options) != 0 || !alive(s)) goto done;
    packet = av_packet_alloc();
    if (!packet) goto done;
    for (;;) {
        if (!alive(s)) goto done;
        int rc = av_read_frame(in, packet);
        if (rc == AVERROR_EOF) break;
        if (rc < 0) goto done;
        int i = packet->stream_index;
        if (i < 0 || i >= 2 || packet->size <= 0 || packet->size > PACKET_MAX ||
            ++packets > PACKETS_MAX) { result = LIMIT; goto done; }
        if (packet->flags & (AV_PKT_FLAG_CORRUPT | AV_PKT_FLAG_DISCARD) ||
            packet->pts == AV_NOPTS_VALUE || packet->dts == AV_NOPTS_VALUE ||
            (last[i] != AV_NOPTS_VALUE && packet->dts <= last[i])) goto done;
        if (!counts[i] && i == video && !(packet->flags & AV_PKT_FLAG_KEY)) goto done;
        if (first[i] == AV_NOPTS_VALUE) first[i] = packet->dts;
        int64_t current_us = av_rescale_q(packet->dts, in->streams[i]->time_base, AV_TIME_BASE_Q);
        int64_t origin_us = av_rescale_q(first[i], in->streams[i]->time_base, AV_TIME_BASE_Q);
        if (current_us < -TIMELINE_MAX_US || current_us > TIMELINE_MAX_US ||
            origin_us < -TIMELINE_MAX_US || origin_us > TIMELINE_MAX_US ||
            current_us - origin_us > TIMELINE_MAX_US) { result = LIMIT; goto done; }
        last[i] = packet->dts;
        counts[i]++;
        av_packet_rescale_ts(packet, in->streams[i]->time_base, out->streams[i]->time_base);
        packet->pos = -1;
        if (av_interleaved_write_frame(out, packet) < 0 || !alive(s)) goto done;
        av_packet_unref(packet);
    }
    if (!counts[video] || !counts[audio] || s->offset != s->size || !alive(s)) goto done;
    if (av_write_trailer(out) < 0 || !alive(s)) goto done;
    avio_flush(writer);
    if (writer->error < 0 || !alive(s) || s->written <= 0) goto done;
    result = OK;
done:
    av_dict_free(&options);
    av_packet_free(&packet);
    if (out) avformat_free_context(out);
    if (opened) avformat_close_input(&in);
    else if (in) avformat_free_context(in);
    free_io(&writer);
    free_io(&reader);
    return s->status ? s->status : result;
}

JNIEXPORT jint JNICALL Java_com_vaydelux_bousygram_core_cast_NativeCastRemuxBridge_run(
    JNIEnv *env, jclass type, jint kind, jlong size, jobject callbacks) {
    (void)type;
    if ((kind != 1 && kind != 2) || size <= 0 || size > INPUT_MAX || !callbacks) return BAD_INPUT;
    Session s = {.env=env, .callbacks=callbacks, .size=size, .deadline=av_gettime_relative()+WALL_US};
    jclass cls = (*env)->GetObjectClass(env, callbacks);
    if (!cls) return IO_FAILED;
    s.read = (*env)->GetMethodID(env, cls, "read", "(J[BI)I");
    if (!s.read) return IO_FAILED;
    s.write = (*env)->GetMethodID(env, cls, "write", "([BI)Z");
    if (!s.write) return IO_FAILED;
    s.active = (*env)->GetMethodID(env, cls, "active", "()Z");
    if (!s.active) return IO_FAILED;
    s.in_array = (*env)->NewByteArray(env, IO_BYTES);
    if (!s.in_array) return IO_FAILED;
    s.out_array = (*env)->NewByteArray(env, IO_BYTES);
    if (!s.out_array) return IO_FAILED;
    /* All callbacks are synchronous on the invoking JNI thread. No global references. */
    return remux(&s, kind);
}
