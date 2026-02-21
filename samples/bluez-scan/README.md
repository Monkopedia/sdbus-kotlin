# Bluez-scan

This uses the adaptor and device interfaces from bluez to scan for bluetooth devices and list them
out. The xml definition of these interfaces can be found in the src/dbusMain directory and the
sdbus-kotlin codegen tool generates wrappers into the shared `commonMain` source set.

The app can be run directly from gradle using the runReleaseExecutableNative task (or
runDevelopmentExecutableNative).

```
$ gradle runReleaseExecutableNative
```

The sample also supports JVM execution:

```
$ gradle runJvm
```
