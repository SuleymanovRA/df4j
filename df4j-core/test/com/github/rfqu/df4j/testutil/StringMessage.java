package com.github.rfqu.df4j.testutil;

public class StringMessage {
    private String str;

    public StringMessage(String str) {
        this.setStr(str);
    }

    @Override
    public String toString() {
        return getStr();
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }
}