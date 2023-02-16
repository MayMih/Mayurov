package org.mmu.tinkoffkinolab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class Utils
{
    /**
     * Причёсанный код отсюда: <a href="https://stackoverflow.com/a/3681726/2323972">...</a>
     */
    public static Bitmap downloadImageBitmap(String url)
    {
        Bitmap bm = null;
        try
        {
            URL aURL = new URL(url);
            URLConnection conn = aURL.openConnection();
            conn.connect();
            try (InputStream is = conn.getInputStream())
            {
                try (BufferedInputStream bis = new BufferedInputStream(is))
                {
                    bm = BitmapFactory.decodeStream(bis);
                }
            }
        }
        catch (Exception e)
        {
            Log.e(Constants.LOG_TAG, "Error getting bitmap", e);
        }
        return bm;
    }
}
