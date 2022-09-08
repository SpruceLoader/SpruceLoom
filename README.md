<div align="center">

# [`UniLoom`]
A fork of Fabric Loom built for use
in the [UniLoader][uniloader] ecosystem.

</div>

## Why does this exist?
Loom depends on some internal Fabric classes, methods and
files which obviously break in non-Fabric environments
where those classes aren't present. We needed to fork the
project so we could adapt it for use with [UniLoader][uniloader]
and [UniLaunchwrapper][launchwrapper].

[uniloader]: ../UniLoader
[launchwrapper]: ../UniLaunchwrapper
