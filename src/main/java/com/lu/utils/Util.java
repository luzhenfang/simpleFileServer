package com.lu.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * @author luckyFang
 * @date 2021/2/5 11:59
 * @file Util.java
 * @desc
 */

public class Util {
    private static class Inner {
        public String getFile(String name) {

            FileInputStream fileInputStream = null;
            StringBuilder buffer = new StringBuilder();
            try {
                int b;
//                fileInputStream = new FileInputStream(this.getClass().getClassLoader().getResource(name).getPath());
                InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(name);
                assert resourceAsStream != null;
                // 乱码解决
                InputStreamReader inputStreamReader = new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8);

                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    buffer.append(line);
                }
                return buffer.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static Logger logger = Logger.getLogger("com.util.logger");

    // 读取文件内容
    public static String readSource(String path) {
        return new Inner().getFile(path);

    }
}
