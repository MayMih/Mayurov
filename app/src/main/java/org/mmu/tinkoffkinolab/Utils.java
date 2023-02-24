package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.LOG_TAG;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    
    public static void extractImageToDiskCache(ImageView imgViewSource, String cachedImageFilePath)
    {
        try (var outStream = new FileOutputStream(cachedImageFilePath))
        {
            Utils.convertDrawableToBitmap(imgViewSource.getDrawable()).compress(
                    Bitmap.CompressFormat.WEBP, 80, outStream);
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "Ошибка записи в файл:\n " + cachedImageFilePath, e);
        }
    }
    
    public static void switchToFullScreen(Activity context)
    {
        // BEGIN_INCLUDE (get_current_ui_flags)
        // The UI options currently enabled are represented by a bitfield.
        // getSystemUiVisibility() gives us that bitfield.
        int uiOptions = context.getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;
        // END_INCLUDE (get_current_ui_flags)
        // BEGIN_INCLUDE (toggle_ui_flags)
        boolean isImmersiveModeEnabled = ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
        if (isImmersiveModeEnabled)
        {
            Log.d(LOG_TAG, "Turning immersive mode mode off. ");
        }
        else
        {
            Log.d(LOG_TAG, "Turning immersive mode mode on.");
        }
        
        // Navigation bar hiding:  Backwards compatible to ICS.
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    
        // Status bar hiding: Backwards compatible to Jellybean
        newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
    
        // Immersive mode: Backward compatible to KitKat.
        // Note that this flag doesn't do anything by itself, it only augments the behavior
        // of HIDE_NAVIGATION and FLAG_FULLSCREEN.  For the purposes of this sample
        // all three flags are being toggled together.
        // Note that there are two immersive mode UI flags, one of which is referred to as "sticky".
        // Sticky immersive mode differs in that it makes the navigation and status bars
        // semi-transparent, and the UI flag does not get cleared when the user interacts with
        // the screen.
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    
        context.getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        //END_INCLUDE (set_ui_flags)
    }
    
    public static void showFullScreenPhoto(Uri photoUri, Activity parentActivity)
    {
        showFullScreenPhoto(photoUri, parentActivity.getBaseContext());
    }
    
    public static void showFullScreenPhoto(Uri photoUri, Context context)
    {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(photoUri, "image/*");
        context.startActivity(intent);
    }
    
    public static boolean isViewOnScreen(View target)
    {
        if (!target.isShown())
        {
            return false;
        }
        final var actualPosition = new Rect();
        final var isGlobalVisible = target.getGlobalVisibleRect(actualPosition);
        final var screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        final var screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        final var screen = new Rect(0, 0, screenWidth, screenHeight);
        return isGlobalVisible && Rect.intersects(actualPosition, screen);
    }
    
    public static void setRoundedBottomToolbarStyle(MaterialToolbar customToolBar, int cornerRadius)
    {
        final var toolbarBackground = (MaterialShapeDrawable) customToolBar.getBackground();
        toolbarBackground.setShapeAppearanceModel(
                toolbarBackground.getShapeAppearanceModel()
                        .toBuilder()
                        .setBottomRightCorner(CornerFamily.ROUNDED, cornerRadius)
                        .setBottomLeftCorner(CornerFamily.ROUNDED, cornerRadius)
                        .build()
        );
    }
}
