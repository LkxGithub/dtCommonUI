package com.nineoldandroids.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Internal class to automatically generate a Property for a given class/name pair, given the
 * specification of {@link com.nineoldandroids.util.Property#of(Class, Class, String)}
 */
class ReflectiveProperty<T, V> extends Property<T, V> {

    private static final String PREFIX_GET = "get";
    private static final String PREFIX_IS = "is";
    private static final String PREFIX_SET = "set";
    private Method mSetter;
    private Method mGetter;
    private Field mField;

    public ReflectiveProperty(Class<T> propertyHolder, Class<V> valueType, String name) {
         // TODO: cache reflection info for each new class/name pair
        super(valueType, name);
        char firstLetter = Character.toUpperCase(name.charAt(0));
        String theRest = name.substring(1);
        String capitalizedName = firstLetter + theRest;
        String getterName = PREFIX_GET + capitalizedName;
        try {
            mGetter = propertyHolder.getMethod(getterName, (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            try {
                /* The native implementation uses JNI to do reflection, which allows access to private methods.
                 * getDeclaredMethod(..) does not find superclass methods, so it's implemented as a fallback.
                 */
                mGetter = propertyHolder.getDeclaredMethod(getterName, (Class<?>[]) null);
                mGetter.setAccessible(true);
            } catch (NoSuchMethodException e2) {
                // getName() not available - try isName() instead
                getterName = PREFIX_IS + capitalizedName;
                try {
                    mGetter = propertyHolder.getMethod(getterName, (Class<?>[]) null);
                } catch (NoSuchMethodException e3) {
                    try {
                        /* The native implementation uses JNI to do reflection, which allows access to private methods.
                         * getDeclaredMethod(..) does not find superclass methods, so it's implemented as a fallback.
                         */
                        mGetter = propertyHolder.getDeclaredMethod(getterName, (Class<?>[]) null);
                        mGetter.setAccessible(true);
                    } catch (NoSuchMethodException e4) {
                        // Try public field instead
                        try {
                            mField = propertyHolder.getField(name);
                            Class fieldType = mField.getType();
                            if (!typesMatch(valueType, fieldType)) {
                                throw new NoSuchPropertyException("Underlying type (" + fieldType + ") " +
                                        "does not match Property type (" + valueType + ")");
                            }
                            return;
                        } catch (NoSuchFieldException e5) {
                            // no way to access property - throw appropriate exception
                            throw new NoSuchPropertyException("No accessor method or field found for"
                                    + " property with name " + name);
                        }
                    }
                }
            }
        }
        Class getterType = mGetter.getReturnType();
        // Check to make sure our getter type matches our valueType
        if (!typesMatch(valueType, getterType)) {
            throw new NoSuchPropertyException("Underlying type (" + getterType + ") " +
                    "does not match Property type (" + valueType + ")");
        }
        String setterName = PREFIX_SET + capitalizedName;
        try {
            // mSetter = propertyHolder.getMethod(setterName, getterType);
            // The native implementation uses JNI to do reflection, which allows access to private methods.
            mSetter = propertyHolder.getDeclaredMethod(setterName, getterType);
            mSetter.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            // Okay to not have a setter - just a readonly property
        }
    }

    private boolean typesMatch(Class<V> valueType, Class getterType) {
        if (getterType != valueType) {
            if (getterType.isPrimitive()) {
                return (getterType == float.class && valueType == Float.class) ||
                        (getterType == int.class && valueType == Integer.class) ||
                        (getterType == boolean.class && valueType == Boolean.class) ||
                        (getterType == long.class && valueType == Long.class) ||
                        (getterType == double.class && valueType == Double.class) ||
                        (getterType == short.class && valueType == Short.class) ||
                        (getterType == byte.class && valueType == Byte.class) ||
                        (getterType == char.class && valueType == Character.class);
            }
            return false;
        }
        return true;
    }

    @Override
    public void set(T object, V value) {
        if (mSetter != null) {
            try {
                mSetter.invoke(object, value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        } else if (mField != null) {
            try {
                mField.set(object, value);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        } else {
            throw new UnsupportedOperationException("Property " + getName() +" is read-only");
        }
    }

    @Override
    public V get(T object) {
        if (mGetter != null) {
            try {
                return (V) mGetter.invoke(object, (Object[])null);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        } else if (mField != null) {
            try {
                return (V) mField.get(object);
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }
        // Should not get here: there should always be a non-null getter or field
        throw new AssertionError();
    }

    /**
     * Returns false if there is no setter or public field underlying this Property.
     */
    @Override
    public boolean isReadOnly() {
        return (mSetter == null && mField == null);
    }
}
