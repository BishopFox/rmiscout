package com.bishopfox.rmiscout;

import java.io.Serializable;

public class ParamPair {
    public Class name;
    public Serializable value;

    public ParamPair(Class name, Serializable value) {
        this.name = name;
        this.value = value;
    }
}
