package ru.hse.clichat.cli;

public class CommandLineArgs {
    private final String username;
    private final String peerHost;
    private final Integer peerPort;

    public CommandLineArgs(String username, String peerHost, Integer peerPort) {
        this.username = username;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
    }

    public String getUsername() { return username; }
    public String getPeerHost() { return peerHost; }
    public Integer getPeerPort() { return peerPort; }

    public boolean isServer() {
        return peerHost == null || peerPort == null;
    }

    public static CommandLineArgs parse(String[] args) {
        String username = null;
        String peerHost = null;
        Integer peerPort = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username":
                    if (i + 1 < args.length) username = args[++i];
                    break;
                case "--peer":
                    if (i + 1 < args.length) peerHost = args[++i];
                    break;
                case "--port":
                    if (i + 1 < args.length) peerPort = Integer.parseInt(args[++i]);
                    break;
            }
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Имя пользователя (--username) обязательно!");
        }

        return new CommandLineArgs(username, peerHost, peerPort);
    }
}
