"""Fake Chaquopy/Android environment for host-side plugin SDK checks.

This module is test scaffolding only: it is never bundled into the APK. It lets a
desktop CPython run Irongram's plugin SDK (``TMessagesProj/src/main/python``) and
import real exteraGram plugins, so import-level and lifecycle regressions show up
in CI instead of on a device.
"""

import sys
import types

JAVA_ROOTS = ("android", "androidx", "java", "javax", "com", "org", "kotlin", "dalvik")


class Dummy:
    """Accepts any attribute access or call, and behaves like a falsy-zero int."""

    def __init__(self, name="Dummy"):
        self._name = name

    def __getattr__(self, item):
        if item.startswith("__"):
            raise AttributeError(item)
        return Dummy("{}.{}".format(self._name, item))

    def __call__(self, *args, **kwargs):
        return Dummy(self._name + "()")

    def __int__(self):
        return 0

    def __bool__(self):
        return True

    def __str__(self):
        return "<stub {}>".format(self._name)

    def __eq__(self, other):
        return self is other

    def __hash__(self):
        return id(self)


class JavaClass(Dummy):
    def __init__(self, name):
        super().__init__(name)
        self._class_name = name

    def __call__(self, *args, **kwargs):
        return Dummy(self._class_name + " instance")


_CLASSES = {}


def jclass(name):
    """Return a stable fake class object for a Java class name."""
    return _CLASSES.setdefault(name, JavaClass(name))


class _ProxyBase:
    def __init__(self, *args, **kwargs):
        pass


def dynamic_proxy(*interfaces):
    """Return a base class that stands in for a Java interface proxy."""
    return _ProxyBase


def _identity(value=0):
    return value


jint = jlong = jshort = jbyte = jfloat = jdouble = jboolean = jchar = _identity


def jarray(kind):
    return lambda values: list(values)


class BuildVars:
    """Values the SDK reads from ``org.telegram.messenger.BuildVars`` in the real app."""

    BUILD_VERSION = 7175
    BUILD_VERSION_STRING = "13.0.2"
    APP_VERSION_NAME = "13.0.2"
    DEBUG_VERSION = False


class _AnyModule(types.ModuleType):
    def __getattr__(self, item):
        if item.startswith("__"):
            raise AttributeError(item)
        return Dummy("{}.{}".format(self.__name__, item))


class _JavaPackageFinder:
    def find_module(self, fullname, path=None):
        if fullname.split(".")[0] in JAVA_ROOTS and fullname != "java":
            return self
        return None

    def load_module(self, fullname):
        if fullname in sys.modules:
            return sys.modules[fullname]
        module = _AnyModule(fullname)
        module.__path__ = []
        sys.modules[fullname] = module
        return module


def install():
    """Install the fake Java environment into ``sys.modules``/``sys.meta_path``."""
    if getattr(install, "_installed", False):
        return
    sys.meta_path.insert(0, _JavaPackageFinder())

    java = types.ModuleType("java")
    java.__path__ = []
    java.jclass = jclass
    java.dynamic_proxy = dynamic_proxy
    java.jint = jint
    java.jlong = jlong
    java.jshort = jshort
    java.jbyte = jbyte
    java.jfloat = jfloat
    java.jdouble = jdouble
    java.jboolean = jboolean
    java.jchar = jchar
    java.jarray = jarray

    chaquopy = _AnyModule("java.chaquopy")
    chaquopy.dynamic_proxy = dynamic_proxy
    chaquopy.jclass = jclass
    chaquopy.jarray = jarray
    sys.modules["java.chaquopy"] = chaquopy
    java.chaquopy = chaquopy

    telegram = _AnyModule("org.telegram.messenger")
    telegram.BuildVars = BuildVars
    sys.modules["org.telegram.messenger"] = telegram

    java.lang = _AnyModule("java.lang")
    java.lang.reflect = _AnyModule("java.lang.reflect")
    sys.modules["java"] = java
    sys.modules["java.lang"] = java.lang
    sys.modules["java.lang.reflect"] = java.lang.reflect
    install._installed = True
