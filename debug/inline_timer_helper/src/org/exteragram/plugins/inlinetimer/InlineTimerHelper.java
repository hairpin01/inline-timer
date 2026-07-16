package org.exteragram.plugins.inlinetimer;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Java helper for inline_timer.plugin.
 *
 * <p>The Python plugin can work without this dex, but this helper keeps the most
 * fragile ChatMessageCell/BotButton reflection in Java and caches fields/methods.
 * It deliberately avoids compile-time references to Telegram classes so it can be
 * compiled against android.jar only.</p>
 */
public final class InlineTimerHelper {
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        PAINT.setTypeface(Typeface.MONOSPACE);
    }

    private InlineTimerHelper() {
    }

    public static boolean drawTimers(Object cell, Canvas canvas, String text, long color, boolean left, String activeSignature) {
        if (cell == null || canvas == null || text == null) {
            return false;
        }

        try {
            Object buttonsObject = field(cell, "botButtons");
            if (!(buttonsObject instanceof List)) {
                return false;
            }

            List<?> buttons = (List<?>) buttonsObject;
            if (buttons.isEmpty()) {
                return true;
            }

            int widthForButtons = intValue(call(cell, "getWidthForButtons"));
            if (widthForButtons <= 0) {
                return false;
            }

            int addX = buttonsAddX(cell, widthForButtons);
            float baseY = intValue(field(cell, "layoutHeight")) - dp(cell, 2.0f);
            Object transition = field(cell, "transitionParams");
            baseY += floatValue(field(transition, "deltaBottom"));
            int pad = dp(cell, 8.0f);

            PAINT.setColor((int) color);
            PAINT.setTextSize(dp(cell, 11.0f));
            PAINT.setTextAlign(left ? Paint.Align.LEFT : Paint.Align.RIGHT);

            for (Object button : buttons) {
                if (button == null || boolValue(field(button, "isSeparator"))) {
                    continue;
                }
                if (activeSignature != null && activeSignature.length() > 0 && !activeSignature.equals(buttonSignature(button))) {
                    continue;
                }

                float buttonX = floatValue(field(button, "x")) * widthForButtons + addX;
                float buttonY = intValue(field(button, "y")) + baseY;
                float buttonWidth = floatValue(field(button, "width")) * widthForButtons;
                int buttonHeight = intValue(field(button, "height"));
                if (buttonWidth <= 0.0f || buttonHeight <= 0) {
                    continue;
                }

                float textX = left ? buttonX + pad : buttonX + buttonWidth - pad;
                float textY = buttonY + (buttonHeight - PAINT.descent() - PAINT.ascent()) / 2.0f;
                canvas.drawText(text, textX, textY, PAINT);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean clearPressedBotButton(Object cell) {
        if (cell == null) {
            return false;
        }

        try {
            Object buttonsObject = field(cell, "botButtons");
            if (!(buttonsObject instanceof List)) {
                return false;
            }

            int pressedIndex = intValue(field(cell, "pressedBotButton"));
            List<?> buttons = (List<?>) buttonsObject;
            if (pressedIndex < 0 || pressedIndex >= buttons.size()) {
                return false;
            }

            Object button = buttons.get(pressedIndex);
            if (button == null) {
                return false;
            }

            Drawable selector = (Drawable) field(button, "selectorDrawable");
            if (selector != null) {
                selector.setState(StateSet.NOTHING);
            }

            Object animator = field(button, "pressAnimator");
            if (animator instanceof ValueAnimator) {
                ((ValueAnimator) animator).cancel();
            }
            setField(button, "pressAnimator", null);
            setField(button, "pressed", false);
            setField(button, "pressT", 0.0f);
            call(button, "setPressed", false);
            call(cell, "invalidate");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int buttonsAddX(Object cell, int widthForButtons) throws Exception {
        Object messageObject = field(cell, "currentMessageObject");
        boolean out = boolValue(call(messageObject, "isOutOwner"));
        int addX;
        if (out) {
            addX = intValue(call(cell, "getMeasuredWidth")) - widthForButtons - dp(cell, 10.0f);
        } else {
            boolean mediaBackground = boolValue(field(cell, "mediaBackground"));
            boolean drawPinnedBottom = boolValue(field(cell, "drawPinnedBottom"));
            addX = intValue(field(cell, "backgroundDrawableLeft")) + dp(cell, (mediaBackground || drawPinnedBottom) ? 1.0f : 7.0f);
        }

        Object transition = field(cell, "transitionParams");
        if (boolValue(field(transition, "animateBackgroundBoundsInner"))) {
            addX += (int) floatValue(field(transition, "deltaLeft"));
        }
        return addX;
    }

    private static int dp(Object view, float value) {
        try {
            Object resources = call(view, "getResources");
            Object metrics = call(resources, "getDisplayMetrics");
            float density = floatValue(field(metrics, "density"));
            return (int) (value * density + 0.5f);
        } catch (Throwable ignored) {
            return (int) value;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        if (target == null) {
            return;
        }
        Field field = findField(target.getClass(), name);
        if (field != null) {
            field.set(target, value);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        String key = clazz.getName() + "#" + name;
        if (FIELDS.containsKey(key)) {
            return FIELDS.get(key);
        }

        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                FIELDS.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        FIELDS.put(key, null);
        return null;
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), name, args.length);
        return method == null ? null : method.invoke(target, args);
    }

    private static Method findMethod(Class<?> clazz, String name, int argCount) {
        String key = clazz.getName() + "#" + name + ":" + argCount;
        if (METHODS.containsKey(key)) {
            return METHODS.get(key);
        }

        Class<?> current = clazz;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == argCount) {
                    method.setAccessible(true);
                    METHODS.put(key, method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        METHODS.put(key, null);
        return null;
    }

    private static String buttonSignature(Object value) throws Exception {
        if (value == null) {
            return "";
        }

        String sig = keyboardButtonSignature(field(value, "button"));
        if (!sig.isEmpty()) {
            return sig;
        }
        sig = keyboardButtonSignature(field(value, "buttonCustom"));
        if (!sig.isEmpty()) {
            return sig;
        }
        sig = keyboardButtonSignature(field(value, "buttonImpl"));
        if (!sig.isEmpty()) {
            return sig;
        }
        return keyboardButtonSignature(value);
    }

    private static String keyboardButtonSignature(Object button) throws Exception {
        if (button == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(button.getClass().getSimpleName());
        appendField(builder, button, "text");
        appendField(builder, button, "data");
        appendField(builder, button, "url");
        appendField(builder, button, "query");
        appendField(builder, button, "same_peer");
        appendField(builder, button, "requires_password");
        appendField(builder, button, "button_id");
        return builder.indexOf("|") >= 0 ? builder.toString() : "";
    }

    private static void appendField(StringBuilder builder, Object target, String name) throws Exception {
        Object value = field(target, name);
        if (value != null) {
            builder.append("|").append(name).append("=").append(signatureValue(value));
        }
    }

    private static String signatureValue(Object value) {
        if (value instanceof byte[]) {
            byte[] data = (byte[]) value;
            StringBuilder hex = new StringBuilder(data.length * 2);
            for (byte item : data) {
                int b = item & 0xff;
                if (b < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(b));
            }
            return hex.toString();
        }
        return String.valueOf(value);
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static float floatValue(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0.0f;
    }

    private static boolean boolValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }
}
