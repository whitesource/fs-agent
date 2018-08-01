package test;

import io.netty.handler.ssl.OpenSslEngine;

public abstract class Hello2{

    public void eat(){
        OpenSslEngine openSslEngine = new OpenSslEngine(2, null, null);
    }
}