package eu.darkbot.hak.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.utils.login.Credentials;
import com.github.manolo8.darkbot.utils.login.LoginUtils;
import eu.darkbot.util.Popups;

import javax.swing.*;
import java.awt.*;
import java.security.GeneralSecurityException;

import static com.github.manolo8.darkbot.Main.API;

public class AuthModule {
    private JFrame mainFrame;
    private Credentials credentials;

    public void handlePw() {
        if (!Main.INSTANCE.config.BOT_SETTINGS.OTHER.DISABLE_MASTER_PASSWORD) {
            Window[] windows = Window.getWindows();

            for (Window window : windows) {
                if (window instanceof JFrame && window.isVisible()) {
                    JFrame frame = (JFrame) window;
                    String title = frame.getTitle();

                    System.out.println("Found window: " + title);

                    // Hide DarkBot main frame
                    if (title != null && title.contains("DarkBot")) {
                        this.mainFrame = frame;
                        frame.setVisible(false);
                        System.out.println("Hid DarkBot frame");
                    }

                    // Hide browser view
                    API.setVisible(false, Main.INSTANCE.config.BOT_SETTINGS.API_CONFIG.FULLY_HIDE_API);
                }
            }

            SwingUtilities.invokeLater(() -> {
                char[] password = requestMasterPassword();
                if (password != null) {
                    decrypt(password);
                    if (mainFrame != null) mainFrame.setVisible(true);
                    API.setVisible(true, Main.INSTANCE.config.BOT_SETTINGS.API_CONFIG.FULLY_HIDE_API);
                } else {
                    System.exit(0);
                }
            });
        } else {
            decrypt(new char[0]);
            System.out.println("SID: " + Main.INSTANCE.backpage.getSid());
            System.out.println("instance URI: " + Main.INSTANCE.backpage.getInstanceURI());
        }
    }

    private char[] requestMasterPassword() {
        JPasswordField passwordField = new JPasswordField(20);

        int result = Popups.of(
                        "Master Password Required",
                        new Object[]{"Input your master password:", passwordField})
                .optionType(JOptionPane.OK_CANCEL_OPTION)
                .showOptionSync();

        if (result == JOptionPane.OK_OPTION) {
            return passwordField.getPassword();
        }
        return null;
    }

    private void decrypt(char[] password) {
        try {
            credentials = LoginUtils.loadCredentials();
            credentials.decrypt(password);
        } catch (GeneralSecurityException e) {
            System.out.println("Master password could not be decrypted");
            System.exit(0);
        }

        System.out.println("Password correct");

        System.out.println("Number of users: " + credentials.getUsers().size());
        for (Credentials.User user : credentials.getUsers()) {
            System.out.println("User: " + user.u);
            System.out.println("User: " + user.p);
        }
    }
}
