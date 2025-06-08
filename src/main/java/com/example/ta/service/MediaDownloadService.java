package com.example.ta.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class MediaDownloadService {

    @Value("${app.media.storage.path:media}")
    private String mediaStoragePath;

    @Value("${app.media.thumbnail.width:300}")
    private int thumbnailWidth;

    @Value("${app.media.thumbnail.height:200}")
    private int thumbnailHeight;

    /**
     * Скачивает медиафайл и создает миниатюру
     */
    public String downloadAndCreateThumbnail(String mediaUrl, String messageId, String mediaType) {
        try {
            // Создаем директории если их нет
            Path mediaDir = Paths.get(mediaStoragePath, "images");
            Path thumbnailDir = Paths.get(mediaStoragePath, "thumbnails");
            Files.createDirectories(mediaDir);
            Files.createDirectories(thumbnailDir);

            // Определяем расширение файла
            String extension = getFileExtension(mediaUrl, mediaType);
            String fileName = "media_" + messageId + "." + extension;
            String thumbnailName = "thumb_" + messageId + ".jpg";

            Path mediaFile = mediaDir.resolve(fileName);
            Path thumbnailFile = thumbnailDir.resolve(thumbnailName);

            // Скачиваем оригинальный файл
            try (InputStream in = new URL(mediaUrl).openStream()) {
                Files.copy(in, mediaFile, StandardCopyOption.REPLACE_EXISTING);
                log.debug("Скачан медиафайл: {}", mediaFile);
            }

            // Создаем миниатюру для изображений
            if ("photo".equals(mediaType) || isImageFile(extension)) {
                createThumbnail(mediaFile.toString(), thumbnailFile.toString());
                log.debug("Создана миниатюра: {}", thumbnailFile);
                return thumbnailFile.toString();
            }

            return mediaFile.toString();

        } catch (Exception e) {
            log.error("Ошибка при скачивании медиафайла: {}", mediaUrl, e);
            return null;
        }
    }

    /**
     * Создает миниатюру изображения
     */
    private void createThumbnail(String originalPath, String thumbnailPath) throws IOException {
        BufferedImage originalImage = ImageIO.read(new File(originalPath));
        
        // Вычисляем размеры с сохранением пропорций
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        double scale = Math.min(
            (double) thumbnailWidth / originalWidth,
            (double) thumbnailHeight / originalHeight
        );
        
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // Создаем миниатюру
        BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnail.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // Сохраняем миниатюру
        ImageIO.write(thumbnail, "jpg", new File(thumbnailPath));
    }

    private String getFileExtension(String url, String mediaType) {
        if ("photo".equals(mediaType)) return "jpg";
        if ("video".equals(mediaType)) return "mp4";
        
        // Пытаемся извлечь расширение из URL
        int lastDot = url.lastIndexOf('.');
        int lastSlash = url.lastIndexOf('/');
        if (lastDot > lastSlash && lastDot != -1) {
            return url.substring(lastDot + 1).toLowerCase();
        }
        
        return "jpg"; // по умолчанию
    }

    private boolean isImageFile(String extension) {
        return extension.matches("(?i)(jpg|jpeg|png|gif|bmp|webp)");
    }

    /**
     * Проверяет существование файла миниатюры
     */
    public boolean thumbnailExists(String thumbnailPath) {
        return thumbnailPath != null && Files.exists(Paths.get(thumbnailPath));
    }
}