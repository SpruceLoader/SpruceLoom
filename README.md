<div align="center">

# [`SpruceLoom`]
A fork of Fabric Loom built for use
in the [Spruce Loader][spruce] ecosystem.

</div>

## Why does this exist?
Loom depends on some internal Fabric classes, methods and
files which obviously break in non-Fabric environments
where those classes aren't present. We needed to fork the
project so we could adapt it for use with [Spruce Loader][spruce]
and [SpruceLaunchwrapper][launchwrapper].

[spruce]: https://spruceloader.xyz/
[launchwrapper]: https://github.com/SpruceLoader/SpruceLaunchwrapper
