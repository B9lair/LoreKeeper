package com.xiaoyu.aiaigent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileOperationToolTest {

    @Test
    void readFile() {
        FileOperationTool tool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String result = tool.readFile(fileName);
        assertNotNull(result);
    }

    @Test
    void writeFile() {
        FileOperationTool tool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String content = "鱼皮是程序员";
        String result = tool.writeFile(fileName,content);
        assertNotNull(result);
    }
}