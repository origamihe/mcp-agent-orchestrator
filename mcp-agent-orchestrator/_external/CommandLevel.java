package com.mcp.common.identity;

public enum CommandLevel {
    OWNER,
    ADMIN,
    USER;

    public boolean canExecute(CommandLevel required) {
        return this.ordinal() <= required.ordinal();
    }
}