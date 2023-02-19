package org.mmu.tinkoffkinolab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

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
    
    /**
     * Метод преобразования изображений из виджета в формат пригодный для записи в файл
     *
     * @implNote Альтернативный вариант - получать {@link Bitmap} из самого {@link ImageView#getDrawingCache()} (устаревшее)
     * @see <a href="https://stackoverflow.com/a/34026527/2323972">Взято отсюда: How can I write a Drawable resource to a File</a>
     */
    public static Bitmap convertDrawableToBitmap(Drawable pd)
    {
        Bitmap bm = Bitmap.createBitmap(pd.getIntrinsicWidth(), pd.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        pd.draw(canvas);
        return bm;
    }
}
