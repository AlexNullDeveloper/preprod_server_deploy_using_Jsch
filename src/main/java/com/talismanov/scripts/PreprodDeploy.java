package com.talismanov.scripts;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PreprodDeploy {
    private static final String CORE_PATH_TO_BYTECODE = "core/build/front_web/WEB-INF/classes/ru";
    private static final String CORE_REMOTE_PATH = "/usr/local/tomcat6/webapps/front_web/WEB-INF/classes/";
    private static final String ANTIFRAUD_PATH_TO_BYTECODE = "antifraud_web/build/ru";
    private static final String ANTIFRAUD_REMOTE_PATH = "/usr/local/tomcat6/webapps/antifraud_web/WEB-INF/classes/";
    private static final String DELIMETER = ";";

    private Properties props;
    private static final String jarName = "deploy_preprod-1.0-jar-with-dependencies.jar";

    /*IMPORTANT*/
    /*if compiling to jar then jarRun == true*/
    private boolean jarRun = false;

    public static void main(String[] arg) {
        new PreprodDeploy().performWork(arg);
    }

    private void performWork(String[] arg) {

        if (arg.length == 0) {
            System.out.println("try --help for more information.");
            return;
        }

        if ("--help".equals(arg[0]) || "-help".equals(arg[0])) {
            System.out.println("Usage: java -jar " + jarName + " [PROJECT]");
            System.out.println("[PROJECT] can be core, antifraud_web etc...");
            System.out.println("use -core to deploy core project and restart tomcat");
            System.out.println("use -antifraud_web to deploy antifraud_web project and restart tomcat");
            return;
        }


        Session session = null;
        Channel channel = null;

        try {
            setAndPrintProperties();

            String project = "";
            String remotePath = props.getProperty("remote.path");
            String path;
            if ("-core".equals(arg[0]) || "--core".equals(arg[0])) {
                System.out.println("need to deploy core");
                path = props.getProperty("base.folder.path") + CORE_PATH_TO_BYTECODE;
                remotePath += "core";
                project = "core";
            } else if ("-antifraud_web".equals(arg[0]) || "--antifraud_web".equals(arg[0])) {
                System.out.println("need to deploy antifraud_web");
                path = props.getProperty("base.folder.path") + ANTIFRAUD_PATH_TO_BYTECODE;
                remotePath += "antifraud_web";
                project = "antifraud_web";
            } else {
                System.out.println("no such option");
                System.out.println("try --help for more information.");
                return;
            }

            JSch jsch = new JSch();

            String user = props.getProperty("user");
            String host = props.getProperty("host");
            String sudoPass = props.getProperty("sudoPass");

            session = jsch.getSession(user, host, Integer.parseInt(props.getProperty("port")));

            MyUserInfo ui = new MyUserInfo();
            //Пробросил пароль для того, чтобы авторизовываться для sudo
            ui.init(props.getProperty("password"));

            session.setUserInfo(ui);
            session.connect();

            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");

            sftpChannel.connect();


            System.out.println("copying from " + path + " to " + remotePath);
            lsFolderCopy(path, remotePath, sftpChannel);


            List<String> commands = new ArrayList<>();
            addCpCommand(commands, remotePath, project);
            populateListOfCommands(commands);

            for (String s : commands) {
                System.out.println("executing command " + s);
                channel = executeCommandWithSudo(session, sudoPass, s);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResourses(session, channel);
        }
    }

    private void setAndPrintProperties() throws IOException {
        this.props = getPropValues();
        printProperties();
    }

    private void printProperties() {
        System.out.println("properties:");
        for (Map.Entry<?, ?> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            System.out.println(key + " : " + value);
        }
    }

    private Properties getPropValues() throws IOException {

        InputStream inputStream = null;
        Properties prop = null;
        try {
            prop = new Properties();
            String propFileName = "config.properties";


            if (jarRun) {
                File jarPath = new File(PreprodDeploy.class.getProtectionDomain().getCodeSource().getLocation().getPath());
                String propertiesPath = jarPath.getParentFile().getAbsolutePath();
                System.out.println(" propertiesPath-" + propertiesPath);
                prop.load(new FileInputStream(propertiesPath + "/" + propFileName));

            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

                if (inputStream != null) {
                    prop.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
                }
            }

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return prop;
    }

    private void populateListOfCommands(List<String> commands) {

        String someCommands = props.getProperty("commands");
        String[] strings = someCommands.split(DELIMETER);
        Collections.addAll(commands, strings);
    }

    private void addCpCommand(List<String> commands, String pathFrom, String project) {
        String command = "";
        if ("core".equals(project)) {
            command = "cp -r " + pathFrom + "/ru " + CORE_REMOTE_PATH;

        } else if ("antifraud_web".equals(project)) {
            command = "cp -r " + pathFrom + "/ru " + ANTIFRAUD_REMOTE_PATH;
        } else {
            System.out.println("this can't happen");
            throw new RuntimeException("addCpCommand");
        }

        System.out.println("command " + command);
        commands.add(0, command);

    }

    private void closeResourses(Session session, Channel channel) {
        if (channel != null) {
            channel.disconnect();
        }
        if (session != null) {
            session.disconnect();
        }
    }

    private Channel executeCommandWithSudo(Session session, String sudoPass, String s) throws JSchException, IOException {

        Channel channel = session.openChannel("exec");

        //
        ChannelExec channelExec = (ChannelExec) channel;

        channelExec.setCommand("sudo -S -p '' " + s);

        OutputStream out = channel.getOutputStream();
        channelExec.setErrStream(System.err);

        channel.connect();

        out.write((sudoPass + "\n").getBytes());
        out.flush();

        InputStream in = channel.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String jarOutput;
        while ((jarOutput = reader.readLine()) != null) {
            System.out.println("stdout = " + jarOutput);
        }
        reader.close();

        //на всякий случай, для ожидания
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return channel;
    }

    private static void lsFolderCopy(String sourcePath, String destPath,
                                     ChannelSftp sftpChannel) throws SftpException, FileNotFoundException {
        File localFile = new File(sourcePath);

        if (localFile.isFile()) {

            //copy if it is a file
            sftpChannel.cd(destPath);

            if (!localFile.getName().startsWith(".")) {
                sftpChannel.put(new FileInputStream(localFile), localFile.getName(), ChannelSftp.OVERWRITE);
            }
        } else {
            System.out.println("copying folder " + localFile.getName());
            File[] files = localFile.listFiles();

            if (files != null && files.length > 0 && !localFile.getName().startsWith(".")) {

                sftpChannel.cd(destPath);
                SftpATTRS attrs = null;

                //check if the directory is already existing
                try {
                    attrs = sftpChannel.stat(destPath + "/" + localFile.getName());
                } catch (Exception e) {
                    System.out.println(destPath + "/" + localFile.getName() + " not found");
                }

                //else create a directory
                if (attrs == null) {
                    sftpChannel.mkdir(localFile.getName());
                }

                for (File file : files) {
                    lsFolderCopy(file.getAbsolutePath(), destPath + "/" + localFile.getName(), sftpChannel);
                }
            }
        }
    }

}
