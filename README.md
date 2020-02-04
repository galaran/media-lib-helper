# Utilities to work with my personal media library (stored on filesystem)

## Library structure
    <root>
        2020_01 (year + month)
            2020-01-01_New Year (date + event name)
                IMG_20200101_000232.jpg (date + time)
                IMG_20200101_000611.jpg (image)
                VID_20200101_001256.mp4 (video)
                PAN_20200101_001531.jpg (panorama)
                REC_20200101_014501.opus (record)
                ...
            IMG_20200113_174312_cat.jpg (with description)
            IMG_20200117_114903.jpg
            SCR_20200120_235832.png (screenshot)
            ...
        2020_02
            2020-02-04_Bike ride
                Forest (subevent, any name)
                    IMG_20200204_123811.jpg
                    IMG_20200204_124330.jpg
                    ...
                IMG_20200204_N1.jpg (date + number)
                IMG_20200204_N2.jpg
                IMG_20200204_N3.jpg
                MAP_20200204_N1_route.png (map)
                ...
            IMG_20200201_235141.jpg
            ...
        ...

## Library validator
Validates library structure/file names and outputs statistics  
Usage: `java me.galaran.medialib.LibraryValidator <media_library_root>`
