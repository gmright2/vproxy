package vproxy.dns.rdata;

/*
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                    ADDRESS                    |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

import vproxy.dns.DNSType;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;
import vproxy.util.Utils;

import java.net.Inet4Address;
import java.util.Objects;

public class A implements RData {
    public Inet4Address address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        A a = (A) o;
        return Objects.equals(address, a.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "A{" +
            "address=" + address +
            '}';
    }

    @Override
    public ByteArray toByteArray() {
        return ByteArray.from(address.getAddress());
    }

    @Override
    public DNSType type() {
        return DNSType.A;
    }

    @Override
    public void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        if (data.length() != 4)
            throw new InvalidDNSPacketException("A record rdata length is not wrong: " + data.length());
        byte[] arr = data.toJavaArray();
        address = (Inet4Address) Utils.l3addr(arr);
    }
}
