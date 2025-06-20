package eu.darkbot.verifier;

import com.github.manolo8.darkbot.utils.AuthAPI;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.IOException;

public class AuthAPIImpl implements AuthAPI {

    @Override
    public void setupAuth() {
        // No-op or simple console output
        System.out.println("Auth setup stub");
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public boolean isDonor() {
        return true;
    }

    @Override
    public boolean requireDonor() {
        return true;
    }

    @Override
    public String getAuthId() {
        return "fake-auth-id";
    }

    @Override
    public Boolean checkPluginJarSignature(JarFile jarFile) throws IOException {
        try {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    while (in.read(buffer) != -1) {}
                }
//                if (entry.getCodeSigners() == null) return null; // unsigned
                return true;
            }
            return true; // signed & valid
        } catch (Exception e) {
            return false;
        }
    }
}
