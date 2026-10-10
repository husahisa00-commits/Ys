package com.example.acagent;

interface IShellService {
    void destroy() = 16777114;
    String exec(String command, int timeoutSec) = 1;
}
