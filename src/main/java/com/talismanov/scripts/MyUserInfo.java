package com.talismanov.scripts;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class MyUserInfo implements UserInfo, UIKeyboardInteractive {

    private String passwd;

    void init(String password) {
        this.passwd = password;
    }

    public String getPassword() {
        return passwd;
    }

    public boolean promptYesNo(String str) {
        return true;
    }

    public String getPassphrase() {
        return null;
    }

    public boolean promptPassphrase(String message) {
        System.out.println("promptPassphrase");
        return true;
    }

    public boolean promptPassword(String message) {
        System.out.println("promptPassword");
        return true;
    }

    public void showMessage(String message) {
    }

    public String[] promptKeyboardInteractive(String destination,
                                              String name,
                                              String instruction,
                                              String[] prompt,
                                              boolean[] echo) {
        return null;
    }
}