package com.lu.handler;

import com.lu.interfaces.Renderable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import com.lu.utils.Util;

import java.io.*;
import java.net.URLDecoder;
import java.util.regex.Pattern;

/**
 * @author luckyFang
 * @date 2021/2/4 14:17
 * @file com.lu.handler.FileServerHandler.java
 * @desc 文件下载服务
 */

public class FileServerHandler extends ChannelInboundHandlerAdapter implements Renderable {
    // 匹配规则
    private static final Pattern LEGAL_PATH = Pattern.compile(".*[<>&\"].*");
    public static final Pattern LEGAL_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
    private static final String NEW_LINE = System.lineSeparator();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (!request.decoderResult().isSuccess()) {
                send(ctx, "请求出现异常", HttpResponseStatus.INTERNAL_SERVER_ERROR); // 500
                return;
            }
            if (request.method() != HttpMethod.GET) {
                send(ctx, "请求方式错误", HttpResponseStatus.METHOD_NOT_ALLOWED);// 405
                return;
            }

            String uri = request.uri();
            String path = sanitizeUri(uri);
            if (path == null) {
                send(ctx, "非法请求", HttpResponseStatus.FORBIDDEN);// 403
                return;
            }
            File file = new File(path);
            if (file.isHidden() || !file.exists()) {
                send(ctx, "文件不存在", HttpResponseStatus.NOT_FOUND);// 403
                return;
            }
            // 如果是文件夹则发送文件列表
            if (file.isDirectory()) {
                if (uri.endsWith("/")) {
                    sendList(ctx, file);
                } else {
                    redirect(ctx, uri + "/");
                }
            }
            // 如果不是则下载文件
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            FileRegion region = new DefaultFileRegion(file, 0, randomAccessFile.length());
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, HttpHeaderValues.ATTACHMENT);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());

            if (HttpUtil.isKeepAlive(request)) {
                HttpUtil.setKeepAlive(response, true);
            }

            ctx.writeAndFlush(response);
            ChannelFuture sendFileFuture = ctx.writeAndFlush(region, ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                    Util.logger.info("文件:" + file.getName() + "传送完毕!");
                    randomAccessFile.close();
                }

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total)
                        throws Exception {
                    if (total < 0) {
                        Util.logger.info("传送进度:" + total);
                    } else {
//                        Util.logger.info("传送"+file.getName()+"进度:"+ progress + "/" + total);
                    }
                }
            });

            // 关闭连接
            if (!HttpUtil.isKeepAlive(request)) {
                sendFileFuture.addListener(ChannelFutureListener.CLOSE);
            }

        }
    }


    // 重定向
    private void redirect(ChannelHandlerContext ctx, String newURI) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newURI);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    // 检测访问路径是否合法
    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (!uri.startsWith("/")) return null;
        uri = uri.replace("/", File.separator);

        // 非法路径检测
        if (uri.contains(File.separator + ".") || uri.contains("." + File.separator) || uri.startsWith(".") || LEGAL_PATH.matcher(uri).matches())
            return null;

        return System.getProperty("user.dir") + uri;
    }


    // 相应页面
    private void send(ChannelHandlerContext ctx, String context, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(context, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain;charset=utf-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    // 渲染列表
    private void sendList(ChannelHandlerContext ctx, File dir) throws IOException {
//        Arrays.asList(dir.listFiles()).forEach(System.out::println);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");

        StringBuilder buffer = new StringBuilder();
        String dirPath = dir.getPath();


        // ==========================渲染=========================
        String indexTemplate = getIndexTemplate();

        StringBuilder listBuilder = new StringBuilder();

        String icon;
        for (File file : dir.listFiles()) {
            // 文件不可读
            if (file.isHidden() || !file.canRead()) {
                continue;
            }
            if (file.isDirectory()) {
                icon = " <i class=\"far fa-folder-open fa-2x\"></i> ";
            } else {
                icon = "<i class=\"far fa-file fa-2x\" aria-hidden=\"true\"></i>";
            }

            String fileName = file.getName();
            listBuilder.append("<li>").append(icon).append("<a href=\"")
                    .append(fileName)
                    .append("\">")
                    .append(fileName)
                    .append("</a></li>").append(NEW_LINE);
        }

        buffer.append(String.format(indexTemplate, getStylesheet(),dirPath, listBuilder.toString()));


        /*
            // ------------renderTemplate-----------------
            buffer.append("<!DOCTYPE html>").append(NEW_LINE);
            buffer.append("<html lang=\"en\"").append(NEW_LINE);
            buffer.append("<head>").append(NEW_LINE);
            buffer.append("    <meta charset=\"UTF-8\">").append(NEW_LINE);
            buffer.append("    <title>Document</title>").append(NEW_LINE);
            buffer.append("<link href=\"https://cdn.bootcdn.net/ajax/libs/font-awesome/5.15.2/css/all.min.css\" rel=\"stylesheet\">").append(NEW_LINE);

            // ---------------- get styleSheet ------------

            buffer.append("<style>").append(getStylesheet()).append("</style>");


            buffer.append("</head>").append(NEW_LINE);
            buffer.append("<body>").append(NEW_LINE);
            buffer.append("<h2>").append(dirPath).append("</h2>").append(NEW_LINE);
            // --------------h2------------
            buffer.append("<ul>").append(NEW_LINE);
            buffer.append("    <li><i class=\"fa fa-undo fa-2x\" aria-hidden=\"true\"></i> <a href=\"../\">上一层</a></li>").append(NEW_LINE);

            String icon ="";
            for (File file : Objects.requireNonNull(dir.listFiles())) {
                // 不可读文件
                if (file.isHidden() || !file.canRead()) {
                    continue;
                }

                if (file.isDirectory()){
                    icon="<i class=\"fa fa-folder fa-2x\" aria-hidden=\"true\"></i>";
                }else{
                    icon="<i class=\"fa fa-file fa-2x\" aria-hidden=\"true\"></i>";
                }

                String fileName = file.getName();
                if (!LEGAL_FILE_NAME.matcher(fileName).matches()) continue;
                buffer.append("<li>").append(icon).append("<a href=\"")
                        .append(fileName)
                        .append("\">")
                        .append(fileName)
                        .append("</a></li>").append(NEW_LINE);
            }
            buffer.append("</ul>").append(NEW_LINE);
            // -------------ul--------------
            // -----------body -------------
            buffer.append("</body>").append(NEW_LINE);
            buffer.append("</html>").append(NEW_LINE);

        */


        ByteBuf byteBuf = Unpooled.copiedBuffer(buffer, CharsetUtil.UTF_8);
        response.content().writeBytes(byteBuf);
        byteBuf.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }


    @Override
    public String getIndexTemplate() {
        return Util.readSource("index.html");
    }

    @Override
    public String getStylesheet() {
        return Util.readSource("style.css");
    }
}
