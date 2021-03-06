package com.hong.myplayer.util;

import android.media.MediaCodecList;

import java.util.HashMap;
import java.util.Map;

public class HVideoSupportUitl {

    private static Map<String, String> codecMap = new HashMap<>();
    static {
        codecMap.put("h264", "video/avc");
    }

    public static String findVideoCodecName(String ffcodeName)
    {
        if(codecMap.containsKey(ffcodeName))
        {
            return codecMap.get(ffcodeName);
        }
        return "";
    }

    public static boolean isSupportCodec(String ffcodecname)
    {
        boolean supportvideo = false;
        int count = MediaCodecList.getCodecCount();
        for(int i = 0; i < count; i++)
        {
            String[] types = MediaCodecList.getCodecInfoAt(i).getSupportedTypes();
            for(int j = 0; j < types.length; j++)
            {
                if(types[j].equals(findVideoCodecName(ffcodecname)))
                {
                    supportvideo = true;
                    break;
                }
            }
            if(supportvideo)
            {
                break;
            }
        }
        return supportvideo;
    }
}
