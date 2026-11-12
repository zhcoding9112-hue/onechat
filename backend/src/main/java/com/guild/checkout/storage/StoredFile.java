package com.guild.checkout.storage;

/**
 * 已存储文件的引用。
 *
 * @param key 存储键，唯一标识一份文件
 * @param url 对外可访问地址，写入业务表的就是它
 * @param sizeBytes 字节数
 * @param contentType 媒体类型
 */
public record StoredFile(String key, String url, long sizeBytes, String contentType) {
}
