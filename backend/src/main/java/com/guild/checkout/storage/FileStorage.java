package com.guild.checkout.storage;

import java.io.InputStream;

/**
 * 文件存储端口。
 *
 * 上线期用本地磁盘实现；design D9 要求凭证走对象存储，换实现时业务侧不需改动。
 * 业务表只存 {@link StoredFile#url}，不存任何与具体存储相关的细节。
 */
public interface FileStorage {

    /**
     * 存入一份文件。
     *
     * @param category    业务分类，决定存放目录，如 talent-audio、payment-proof
     * @param originalName 原始文件名，仅用于推断扩展名
     * @param contentType 媒体类型
     * @param sizeBytes   字节数
     * @param content     文件内容
     */
    StoredFile store(String category, String originalName, String contentType,
                     long sizeBytes, InputStream content);

    /** 删除一份文件。文件不存在时静默返回。 */
    void delete(String key);
}
