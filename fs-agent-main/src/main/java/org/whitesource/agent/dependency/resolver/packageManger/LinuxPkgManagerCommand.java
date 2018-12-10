package org.whitesource.agent.dependency.resolver.packageManger;

public enum LinuxPkgManagerCommand {

    DEBIAN("dpkg -l"),
    RPM("rpm -qa"),
    ALPINE("apk -vv info"),
    ARCH_LINUX("pacman -Q");

    private String command;

    LinuxPkgManagerCommand(String url) {
        this.command = url;
    }

    public String getCommand() {
        return command;
    }
}
