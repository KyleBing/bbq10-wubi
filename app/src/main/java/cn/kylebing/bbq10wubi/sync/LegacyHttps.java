package cn.kylebing.bbq10wubi.sync;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import cn.kylebing.bbq10wubi.internal.bc.tls.CertificateRequest;
import cn.kylebing.bbq10wubi.internal.bc.tls.CertificateStatusRequest;
import cn.kylebing.bbq10wubi.internal.bc.tls.CipherSuite;
import cn.kylebing.bbq10wubi.internal.bc.tls.DefaultTlsClient;
import cn.kylebing.bbq10wubi.internal.bc.tls.NameType;
import cn.kylebing.bbq10wubi.internal.bc.tls.ProtocolVersion;
import cn.kylebing.bbq10wubi.internal.bc.tls.ServerName;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsAuthentication;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsClientProtocol;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsCredentials;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsFatalAlert;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsServerCertificate;
import cn.kylebing.bbq10wubi.internal.bc.tls.TlsUtils;
import cn.kylebing.bbq10wubi.internal.bc.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * Q10 / Android 4.3 的 OpenSSL 没有 AES-GCM，门户只提供 GCM/ChaCha20。
 * 系统自带的 org.bouncycastle 也很旧，会挡住新版 BC。
 * 因此用重定位后的 BouncyCastle 纯 Java TLS，完全不走 s23_clnt.c。
 */
public final class LegacyHttps {
    private static final String TAG = "LegacyHttps";
    private static X509TrustManager trust;
    private static boolean installed;

    private LegacyHttps() {}

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        try {
            trust = buildTrust(context.getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "trust", t);
        }
    }

    public static String friendlyError(Throwable t) {
        Throwable c = t;
        StringBuilder sb = new StringBuilder();
        while (c != null) {
            String name = c.getClass().getSimpleName();
            String msg = c.getMessage();
            String lower = msg == null ? "" : msg.toLowerCase(Locale.US);
            if (c instanceof TlsFatalAlert
                    || name.contains("SSL") || name.contains("Handshake")
                    || lower.contains("ssl") || lower.contains("handshake")
                    || lower.contains("trust") || lower.contains("cert")
                    || lower.contains("tls")) {
                return "HTTPS 连接失败（设备系统过旧）：" + (msg == null ? name : msg);
            }
            if (sb.length() < 180) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(msg == null ? name : msg);
            }
            c = c.getCause();
        }
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    static final class Response {
        final int code;
        final String body;
        final String accessToken;

        Response(int code, String body, String accessToken) {
            this.code = code;
            this.body = body;
            this.accessToken = accessToken;
        }
    }

    static Response execute(String method, String urlStr, byte[] jsonBody, String authorization)
            throws IOException {
        URL url = new URL(urlStr);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("只支持 HTTPS：" + url.getProtocol());
        }
        String host = url.getHost();
        int port = url.getPort();
        if (port < 0) port = 443;
        String path = url.getFile();
        if (path == null || path.length() == 0) path = "/";

        Socket socket = new Socket();
        TlsClientProtocol tls = null;
        try {
            socket.connect(new InetSocketAddress(host, port), 15000);
            socket.setSoTimeout(30000);
            tls = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
            handshake(tls, host);
            writeRequest(tls.getOutputStream(), method, path, host, port, jsonBody, authorization);
            return readResponse(tls.getInputStream());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("SSL handshake failed: " + e.getMessage(), e);
        } finally {
            if (tls != null) {
                try { tls.close(); } catch (Exception ignored) {}
            }
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static void handshake(TlsClientProtocol tls, final String host) throws IOException {
        final X509TrustManager tm = trust;
        BcTlsCrypto crypto = new BcTlsCrypto(new SecureRandom());
        tls.connect(new DefaultTlsClient(crypto) {
            @Override
            protected ProtocolVersion[] getSupportedVersions() {
                return new ProtocolVersion[] { ProtocolVersion.TLSv12 };
            }

            @Override
            protected int[] getSupportedCipherSuites() {
                return TlsUtils.getSupportedCipherSuites(getCrypto(), new int[] {
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
                });
            }

            @Override
            protected Vector getSNIServerNames() {
                Vector names = new Vector();
                try {
                    names.addElement(new ServerName(NameType.host_name, host.getBytes("US-ASCII")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return names;
            }

            @Override
            protected CertificateStatusRequest getCertificateStatusRequest() {
                return null;
            }

            @Override
            public TlsAuthentication getAuthentication() {
                return new TlsAuthentication() {
                    @Override
                    public void notifyServerCertificate(TlsServerCertificate serverCertificate)
                            throws IOException {
                        verifyServer(host, tm, serverCertificate);
                    }

                    @Override
                    public TlsCredentials getClientCredentials(CertificateRequest request) {
                        return null;
                    }
                };
            }
        });
    }

    private static void verifyServer(String host, X509TrustManager tm,
                                     TlsServerCertificate serverCertificate) throws IOException {
        try {
            cn.kylebing.bbq10wubi.internal.bc.tls.Certificate tlsCerts =
                    serverCertificate.getCertificate();
            if (tlsCerts == null || tlsCerts.getLength() == 0) {
                throw new CertificateException("empty certificate chain");
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate[] chain = new X509Certificate[tlsCerts.getLength()];
            for (int i = 0; i < tlsCerts.getLength(); i++) {
                byte[] der = tlsCerts.getCertificateAt(i).getEncoded();
                chain[i] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            }
            if (tm != null) {
                tm.checkServerTrusted(chain, "RSA");
            } else {
                throw new CertificateException("no trust manager");
            }
            verifyHostname(host, chain[0]);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("证书校验失败: " + e.getMessage(), e);
        }
    }

    private static void verifyHostname(String host, X509Certificate cert) throws CertificateException {
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        if (sans != null) {
            for (List<?> san : sans) {
                if (san == null || san.size() < 2) continue;
                Object type = san.get(0);
                if (type instanceof Integer && ((Integer) type).intValue() == 2) {
                    if (hostMatches(host, String.valueOf(san.get(1)))) return;
                }
            }
        }
        throw new CertificateException("主机名不匹配: " + host);
    }

    private static boolean hostMatches(String host, String pattern) {
        if (host == null || pattern == null) return false;
        String h = host.toLowerCase(Locale.US);
        String p = pattern.toLowerCase(Locale.US);
        if (h.equals(p)) return true;
        if (p.startsWith("*.")) {
            String suffix = p.substring(1);
            return h.endsWith(suffix) && h.indexOf('.') == h.length() - suffix.length();
        }
        return false;
    }

    private static void writeRequest(OutputStream out, String method, String path, String host,
                                     int port, byte[] jsonBody, String authorization) throws IOException {
        StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        if (port == 443) req.append("Host: ").append(host).append("\r\n");
        else req.append("Host: ").append(host).append(':').append(port).append("\r\n");
        req.append("Accept: application/json\r\n");
        req.append("Connection: close\r\n");
        if (authorization != null && authorization.length() > 0) {
            req.append("Authorization: ").append(authorization).append("\r\n");
        }
        if (jsonBody != null) {
            req.append("Content-Type: application/json; charset=utf-8\r\n");
            req.append("Content-Length: ").append(jsonBody.length).append("\r\n");
        }
        req.append("\r\n");
        out.write(req.toString().getBytes("US-ASCII"));
        if (jsonBody != null) out.write(jsonBody);
        out.flush();
    }

    private static Response readResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) throw new EOFException("empty HTTPS response");
        int code = parseStatus(statusLine);
        String accessToken = null;
        int contentLength = -1;
        boolean chunked = false;
        while (true) {
            String line = readLine(in);
            if (line == null) throw new EOFException("truncated headers");
            if (line.length() == 0) break;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.equalsIgnoreCase("X-Access-Token")) accessToken = value;
            else if (name.equalsIgnoreCase("Content-Length")) {
                try { contentLength = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
            } else if (name.equalsIgnoreCase("Transfer-Encoding")
                    && value.toLowerCase(Locale.US).contains("chunked")) {
                chunked = true;
            }
        }
        byte[] raw;
        if (chunked) raw = readChunked(in);
        else if (contentLength >= 0) raw = readFully(in, contentLength);
        else raw = readAll(in);
        String body = new String(raw, "UTF-8");
        return new Response(code, body, accessToken);
    }

    private static int parseStatus(String statusLine) throws IOException {
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) throw new IOException("bad status: " + statusLine);
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("bad status: " + statusLine);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (prev == '\r') buf.write(prev);
            if (b != '\r') buf.write(b);
            prev = b;
        }
        return buf.toString("US-ASCII");
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] out = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(out, off, n - off);
            if (r < 0) throw new EOFException("body truncated");
            off += r;
        }
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) throw new EOFException("truncated chunk");
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
            int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                throw new IOException("bad chunk size: " + sizeLine);
            }
            if (size == 0) {
                while (true) {
                    String trailer = readLine(in);
                    if (trailer == null || trailer.length() == 0) break;
                }
                break;
            }
            out.write(readFully(in, size));
            String crlf = readLine(in);
            if (crlf == null) throw new EOFException("truncated chunk crlf");
        }
        return out.toByteArray();
    }

    private static X509TrustManager buildTrust(Context context) throws Exception {
        final X509TrustManager system = systemTrust();
        final X509TrustManager letsEncrypt = letsEncryptTrust(context);
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (system != null) system.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                try {
                    if (system != null) {
                        system.checkServerTrusted(chain, authType);
                        return;
                    }
                } catch (Exception ignored) {
                }
                if (letsEncrypt != null) {
                    letsEncrypt.checkServerTrusted(chain, authType);
                    return;
                }
                throw new CertificateException("no trust manager");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return system == null ? new X509Certificate[0] : system.getAcceptedIssuers();
            }
        };
    }

    private static X509TrustManager systemTrust() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static X509TrustManager letsEncryptTrust(Context context) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null);
            addAssetCert(context, cf, ks, "certs/isrgrootx1.pem", "isrgrootx1");
            addAssetCert(context, cf, ks, "certs/isrg-root-yr-x1.pem", "isrgry_x1");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
            }
        } catch (Exception e) {
            Log.w(TAG, "ISRG root", e);
        }
        return null;
    }

    private static void addAssetCert(Context context, CertificateFactory cf, KeyStore ks,
                                     String asset, String alias) {
        InputStream in = null;
        try {
            in = context.getAssets().open(asset);
            ks.setCertificateEntry(alias, cf.generateCertificate(in));
        } catch (Exception e) {
            Log.w(TAG, asset, e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }
}
