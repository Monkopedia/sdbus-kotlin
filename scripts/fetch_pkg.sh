#!/bin/bash

VERSION=$1
function fetchArch() {
    ARCH=$1
    echo "Fetching $ARCH and placing in libs/$ARCH/$VERSION"

    rm -rf libs/$ARCH/$VERSION
    mkdir -p libs/$ARCH/$VERSION
    cd libs/$ARCH/$VERSION

    if [[ "$ARCH" == x86_64 ]]; then
       URL=https://archlinux.org/packages/core/$ARCH/systemd-libs/download/
    elif [[ "$ARCH" == aarch64 ]]; then
       URL=http://mirror.archlinuxarm.org/$ARCH/core/systemd-libs-$VERSION-$ARCH.pkg.tar.xz
    fi
    wget $URL -Opackage.pkg.tar.zst
    tar xf package.pkg.tar.zst usr/include usr/lib
    mv usr/include include/
    mv usr/lib lib/
    rmdir usr
    rm package.pkg.tar.zst
    cd -
}
fetchArch x86_64
fetchArch aarch64

cat > src/nativeInterop/cinterop/sdbus-$VERSION.def <<EOF
headers = systemd/sd-bus.h
compilerOpts.linux = -Ilibs/aarch64/$VERSION/include
linkerOpts.linux = -Llibs/aarch64/$VERSION/lib -l systemd -l c
compilerOpts.linux_x64 = -Ilibs/x86_64/$VERSION/include
linkerOpts.linux_x64 = -Llibs/x86_64/$VERSION/lib -l systemd -l c
package = sdbus

---
#include <systemd/sd-bus.h>

void sd_bus_vtable_start(sd_bus_vtable* ret, uint64_t flags) {
    sd_bus_vtable start = SD_BUS_VTABLE_START(flags);
    *ret = start;
};

void sd_bus_signal_with_names(sd_bus_vtable* ret, char *member
                                         , char *signature
                                         , char *outnames
                                         , uint64_t flags )
{
    sd_bus_vtable vt = SD_BUS_SIGNAL_WITH_NAMES(member, signature, outnames, flags);
    *ret = vt;
}

void sd_bus_property(sd_bus_vtable* ret, char *member
                                                   , char *signature
                                                   , sd_bus_property_get_t getter
                                                   , uint64_t flags )
{
    sd_bus_vtable vt = SD_BUS_PROPERTY(member, signature, getter, 0, flags);
    *ret = vt;
}

void sd_bus_writable_property(sd_bus_vtable* ret, char *member
                                                   , char *signature
                                                   , sd_bus_property_get_t getter
                                                   , sd_bus_property_set_t setter
                                                   , uint64_t flags )
{
    sd_bus_vtable vt = SD_BUS_WRITABLE_PROPERTY(member, signature, getter, setter, 0, flags);
    *ret = vt;
}

void sd_bus_vtable_end(sd_bus_vtable* ret)
{
    sd_bus_vtable vt = SD_BUS_VTABLE_END;
    *ret = vt;
}
EOF


