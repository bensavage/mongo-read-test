package com.ben;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class MyState implements MyBehaviour.State {

    public MyState() {
    }

    private transient AtomicInteger counter = new AtomicInteger();

    public void increment() {
        counter.incrementAndGet();
    }

}