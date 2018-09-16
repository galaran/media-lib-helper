package me.galaran.medialib.legacy;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PhotoRenamer {

    public static void main(String[] args) throws Exception {
        Files.walk(Paths.get("c:\\Dropbox\\archive\\Photos\\2016_08\\2016-08-18_Велопуть к метро\\"), 1)
                .filter(path -> path.toString().matches(".+\\.(jpg|jpeg|png)$"))
                .filter(path -> !path.getFileName().toString().matches("^IMG_\\d{8}_\\d{6}\\.\\w+$"))
                .forEach(path -> {
                    try {
                        Instant modTime = Files.getLastModifiedTime(path).toInstant();
                        String ext = path.getFileName().toString().substring(path.getFileName().toString().lastIndexOf(".") + 1);
                        String newName = "IMG_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                .withZone(ZoneId.systemDefault()).format(modTime) + "." + ext;
                        System.out.println("Rename " + path + " to " + newName);
                        Files.move(path, path.getParent().resolve(newName));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }
}
