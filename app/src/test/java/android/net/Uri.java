package android.net;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM test mock for android.net.Uri to enable unit testing of Media3 DataSpec and TdLibDataSource
 * without requiring the full Android framework or Robolectric.
 */
public abstract class Uri implements Comparable<Uri> {

    public static final Uri EMPTY = new StringUri("");

    public static Uri parse(String uriString) {
        if (uriString == null) return null;
        return new StringUri(uriString);
    }

    public static Uri fromFile(java.io.File file) {
        if (file == null) return null;
        return new StringUri("file://" + file.getAbsolutePath());
    }

    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        if (baseUri == null) return null;
        return baseUri.buildUpon().appendPath(pathSegment).build();
    }

    public static String encode(String s) {
        if (s == null) return null;
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public static String decode(String s) {
        if (s == null) return null;
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public abstract String getScheme();
    public abstract String getHost();
    public abstract String getPath();
    public abstract String getLastPathSegment();
    public abstract String getQuery();
    public abstract String getQueryParameter(String key);
    public abstract List<String> getPathSegments();
    public abstract Builder buildUpon();

    @Override
    public abstract String toString();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Uri)) return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compareTo(Uri o) {
        return toString().compareTo(o.toString());
    }

    public static class Builder {
        private String scheme;
        private String authority;
        private String path = "";
        private final Map<String, String> queryParams = new LinkedHashMap<>();

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder authority(String authority) {
            this.authority = authority;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder appendPath(String segment) {
            if (this.path == null || this.path.isEmpty()) {
                this.path = "/" + segment;
            } else if (this.path.endsWith("/")) {
                this.path = this.path + segment;
            } else {
                this.path = this.path + "/" + segment;
            }
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            queryParams.put(key, value);
            return this;
        }

        public Uri build() {
            StringBuilder sb = new StringBuilder();
            if (scheme != null) {
                sb.append(scheme).append("://");
            }
            if (authority != null) {
                sb.append(authority);
            }
            if (path != null && !path.isEmpty()) {
                if (!path.startsWith("/") && (authority != null || scheme != null)) {
                    sb.append("/");
                }
                sb.append(path);
            }
            if (!queryParams.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) sb.append("&");
                    first = false;
                    try {
                        sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                          .append("=")
                          .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                    } catch (Exception e) {
                        sb.append(entry.getKey()).append("=").append(entry.getValue());
                    }
                }
            }
            return new StringUri(sb.toString());
        }
    }

    private static class StringUri extends Uri {
        private final String uriString;
        private final java.net.URI javaUri;

        StringUri(String uriString) {
            this.uriString = uriString;
            java.net.URI parsed = null;
            try {
                parsed = java.net.URI.create(uriString);
            } catch (Exception ignored) {}
            this.javaUri = parsed;
        }

        @Override
        public String getScheme() {
            if (javaUri != null && javaUri.getScheme() != null) return javaUri.getScheme();
            int idx = uriString.indexOf("://");
            return idx > 0 ? uriString.substring(0, idx) : null;
        }

        @Override
        public String getHost() {
            if (javaUri != null && javaUri.getHost() != null) return javaUri.getHost();
            int schemeIdx = uriString.indexOf("://");
            if (schemeIdx <= 0) return null;
            String afterScheme = uriString.substring(schemeIdx + 3);
            int end = afterScheme.indexOf('/');
            if (end < 0) end = afterScheme.indexOf('?');
            if (end < 0) end = afterScheme.indexOf(':');
            return end > 0 ? afterScheme.substring(0, end) : afterScheme;
        }

        @Override
        public String getPath() {
            return javaUri != null ? javaUri.getPath() : null;
        }

        @Override
        public String getLastPathSegment() {
            List<String> segments = getPathSegments();
            return segments.isEmpty() ? null : segments.get(segments.size() - 1);
        }

        @Override
        public List<String> getPathSegments() {
            List<String> list = new ArrayList<>();
            String p = getPath();
            if (p != null) {
                for (String s : p.split("/")) {
                    if (!s.isEmpty()) list.add(s);
                }
            }
            return list;
        }

        @Override
        public String getQuery() {
            int q = uriString.indexOf('?');
            return q >= 0 ? uriString.substring(q + 1) : null;
        }

        @Override
        public String getQueryParameter(String key) {
            String query = getQuery();
            if (query == null || query.isEmpty()) return null;
            for (String param : query.split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0) {
                    String k = param.substring(0, eq);
                    if (k.equals(key)) {
                        try {
                            return URLDecoder.decode(param.substring(eq + 1), "UTF-8");
                        } catch (Exception e) {
                            return param.substring(eq + 1);
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public Builder buildUpon() {
            Builder b = new Builder();
            b.scheme(getScheme());
            b.authority(getHost());
            b.path(getPath());
            String query = getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    int eq = param.indexOf('=');
                    if (eq > 0) {
                        b.appendQueryParameter(param.substring(0, eq), param.substring(eq + 1));
                    }
                }
            }
            return b;
        }

        @Override
        public String toString() {
            return uriString;
        }
    }
}
