package com.gexoty.picolibrary;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.WeakHashMap;

/** Use the same icon contour for hardware rendering and software captures. */
public final class IconMask {
    private IconMask() {}
    private static final WeakHashMap<View,Shape> SHAPES=new WeakHashMap<>();
    private static final class Shape {
        final Path path=new Path();int width=-1,height=-1;float radius=-1;boolean icon;
        Shape(View view){try{String id=view.getResources().getResourceEntryName(view.getId());icon="app_icon_logo".equals(id)||"appIconIV".equals(id)||"app_icon".equals(id);}catch(Exception ignored){}}
    }
    public static int begin(View view,Canvas canvas) {
        int saved=canvas.save();int width=view.getWidth(),height=view.getHeight();if(width<=0||height<=0)return saved;
        Shape shape=SHAPES.get(view);if(shape==null){shape=new Shape(view);SHAPES.put(view,shape);}
        float radius=0;
        if(shape.icon)radius=Math.min(width,height)*0.22f;
        else try {
            radius=((Number)view.getClass().getMethod("getRound").invoke(view)).floatValue();
            if(Float.isNaN(radius)||radius<=0)radius=Math.min(width,height)*((Number)view.getClass().getMethod("getRoundPercent").invoke(view)).floatValue()/2;
        }catch(Exception ignored){}
        if(radius>0) {
            if(shape.width!=width||shape.height!=height||shape.radius!=radius){shape.width=width;shape.height=height;shape.radius=radius;shape.path.reset();shape.path.addRoundRect(new RectF(0,0,width,height),radius,radius,Path.Direction.CW);}
            canvas.clipPath(shape.path);
        }
        return saved;
    }
}
