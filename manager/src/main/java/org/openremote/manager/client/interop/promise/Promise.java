package org.openremote.manager.client.interop.promise;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import org.openremote.manager.client.interop.BiConsumer;
import org.openremote.manager.client.interop.Consumer;
import org.openremote.manager.client.interop.Function;

@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Promise")
public class Promise<T,U> {

    public Promise(BiConsumer<Consumer<T>,Consumer<U>> fn) {}

    public Promise() {}

    public native <V,W> Promise<V,W> then(Function<T,Promise<V,W>> f);

    public native Promise<T,U> then(Consumer<T> f);

    public native <V,W> Promise<V,W> then(Function<T,Promise<V,W>> resolve, Function<U,Promise<V,W>> reject);

    public native Promise<T,U> then(Consumer<T> resolve, Consumer<T> reject);

    @JsMethod(name = "catch")
    public native <V,W> Promise<V,W> catchException(Function<U,Promise<V,W>> error);

    @JsMethod(name = "catch")
    public native Promise<T,U> catchException(Consumer<U> error);

    public static native <T> Promise resolve(T obj);

    public static native <U> Promise reject(U obj);

    public static native <T,U> Promise<T,U> all(Promise<T,U>... promises);

    public static native <T,U> Promise<T,U> race(Promise<T,U>... promises);
}