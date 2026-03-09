package com.mantra.marvisauthapp;

public class IrisUser {
    public int id;
    public String name;
    public boolean hasLeftEye;
    public boolean hasRightEye;

    public IrisUser(int id, String name, boolean hasLeftEye, boolean hasRightEye) {
        this.id = id;
        this.name = name;
        this.hasLeftEye = hasLeftEye;
        this.hasRightEye = hasRightEye;
    }
}