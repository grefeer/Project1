package com.aiqa.project1.worker;


import com.aiqa.project1.nodes.State;

public interface Node {
    public State run(State state);
}
