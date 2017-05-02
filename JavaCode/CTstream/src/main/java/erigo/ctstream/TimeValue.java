package erigo.ctstream;

// MJM storage for time-stamped images
public class TimeValue {
    public long time;
    public byte[] value;

    public TimeValue(long t, byte[] v) {
        time = t;
        value = v;
    }
}