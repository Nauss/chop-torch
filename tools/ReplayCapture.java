import fr.nauss.choptorch.ChopDetector;
import fr.nauss.choptorch.SampleFreshness;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** PRIVATE replay of the actual compiled engine. No translated recognizer.
 * Reflection only supports transition diagnostics on the historical 0.2 engine.
 * These legacy causes are inferred from its exact guards; new engines emit events.
 */
class ReplayCapture {
    static Object field(ChopDetector d, String name) throws Exception {
        Field f = ChopDetector.class.getDeclaredField(name); f.setAccessible(true); return f.get(d);
    }
    static void inc(Map<String,Integer> counts, String name) { counts.merge(name, 1, Integer::sum); }
    static ChopDetector detector(int sensitivity, Map<String,Integer> counts, double[] clock, boolean detail) throws Exception {
        try {
            Class<?> observer = Class.forName("fr.nauss.choptorch.ChopDetector$Observer");
            Object sink = Proxy.newProxyInstance(observer.getClassLoader(), new Class<?>[]{observer}, (proxy, method, args) -> {
                if (method.getName().equals("onEvent")) {
                    inc(counts, args[0].toString());
                    if (detail) System.out.printf(Locale.ROOT,"s=%d t=%.6f event=%s%n", sensitivity, clock[0], args[0]);
                }
                return null;
            });
            return (ChopDetector)ChopDetector.class.getConstructor(int.class, observer).newInstance(sensitivity, sink);
        } catch (ClassNotFoundException historical) { return new ChopDetector(sensitivity); }
    }
    // Development-only reflection: never ships in the APK. Fail closed on schema drift.
    static Object value(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static String snapshot(ChopDetector d) throws Exception {
        StringBuilder s = new StringBuilder("gravity="+Arrays.toString((double[])value(d,"gravity"))
            +" warmUntil="+value(d,"warmUntil")+" cooldownUntil="+value(d,"cooldownUntil"));
        for (Object scale : (List<?>)value(d,"scales")) {
            s.append(" {arm=").append(value(scale,"arm"));
            for (String key : new String[]{"phase","began","phaseAt","supportNs","supportedLast","accelerationPeak","brakingPeak","firstAt"})
                s.append(" ").append(key).append("=").append(value(scale,key));
            s.append(" direction=").append(Arrays.toString((double[])value(scale,"direction")));
            s.append(" firstDirection=").append(Arrays.toString((double[])value(scale,"firstDirection")));
            try {
                s.append(" accelerationIntegral=").append(Arrays.toString((double[])value(scale,"accelerationIntegral")));
                s.append(" firstTravel=").append(Arrays.toString((double[])value(scale,"firstTravel")));
            } catch (NoSuchFieldException historical03) { s.append(" sustainedWitness=UNAVAILABLE"); }
            s.append("}");
        }
        return s.toString();
    }
    static void state(int sensitivity, double ms, String admission, String before, ChopDetector d) throws Exception {
        System.out.printf(Locale.ROOT,"STATE s=%d t=%.6f admission=%s before=%s after=%s%n",sensitivity,ms,admission,before,snapshot(d));
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("CSV, mode, optional --detail/--state required");
        boolean probe = args.length == 3 && args[2].equals("--state");
        if (args.length == 3 && !probe && !args[2].equals("--detail")) throw new IllegalArgumentException("Unknown probe option");
        String mode = args[1]; boolean detail = args.length == 3;
        if (!Set.of("detector-only", "recorded-freshness", "recomputed-freshness").contains(mode)) throw new IllegalArgumentException("Unknown admission mode");
        boolean legacy;
        try { Class.forName("fr.nauss.choptorch.ChopDetector$Observer"); legacy = false; }
        catch (ClassNotFoundException historical) { legacy = true; }
        if (probe && legacy) throw new IllegalArgumentException("State probe requires fixed-scale engine; historical replay remains available without --state");
        List<String> rows = Files.readAllLines(Path.of(args[0]));
        for (int sensitivity : new int[]{0, 50, 100}) {
            Map<String,Integer> counts = new TreeMap<>();
            double[] clock = {0};
            ChopDetector d = detector(sensitivity, counts, clock, detail);
            List<Double> hits = new ArrayList<>();
            int mismatches = 0, admissionRejected = 0;
            for (String row : rows) {
                String[] c = row.split(",");
                if (c.length != 4 && c.length != 7) throw new IllegalArgumentException("Expected four or seven columns");
                double ms = Double.parseDouble(c[0]);
                clock[0] = ms;
                long ns = 1_000_000_000L + Math.round(ms * 1e6);
                double[] raw = {Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3])};
                String beforeState = probe ? snapshot(d) : null;
                if (!mode.equals("detector-only")) {
                    if (c.length != 7) throw new IllegalArgumentException("V1 lacks receipt/admission data");
                    long received = 1_000_000_000L + Math.round(Double.parseDouble(c[4]) * 1e6);
                    boolean recorded = Boolean.parseBoolean(c[5]);
                    boolean computed = SampleFreshness.INSTANCE.accepts(ns, received);
                    if (recorded != computed) mismatches++;
                    if (!(mode.equals("recorded-freshness") ? recorded : computed)) {
                        d.cancel(); admissionRejected++;
                        if (probe) state(sensitivity, ms, "REJECTED", beforeState, d);
                        continue;
                    }
                }
                int before = legacy ? (int)field(d,"phase") : 0;
                long at = legacy ? (long)field(d,"phaseAt") : 0, began = legacy ? (long)field(d,"began") : 0, firstAt = legacy ? (long)field(d,"firstAt") : 0;
                long support = legacy ? (long)field(d,"supportNs") : 0, last = legacy ? (long)field(d,"last") : 0;
                double[] dir = legacy ? ((double[])field(d,"direction")).clone() : null;
                double[] previous = legacy ? (double[])field(d,"firstDirection") : null;
                boolean hit = d.sample(ns,raw[0],raw[1],raw[2]);
                if (probe) state(sensitivity, ms, mode.equals("detector-only") ? "BYPASSED" : "ACCEPTED", beforeState, d);
                int after = legacy ? (int)field(d,"phase") : 0;
                if (hit) hits.add(ms);
                if (legacy) {
                    // Only classify 0.2; never interpret candidate guards as legacy.
                    boolean invalid = ns <= 0 || Arrays.stream(raw).anyMatch(v -> !Double.isFinite(v) || Math.abs(v)>160);
                    boolean discontinuity = !invalid && (last==0 || ns<=last || ns-last>120_000_000L);
                    double[] g=(double[])field(d,"gravity"); double dot=0;
                    for(int i=0;i<3;i++) dot+=(raw[i]-g[i])*dir[i];
                    double threshold=18.0-sensitivity*0.10;
                    long elapsed=ns-at;
                    if(invalid) inc(counts,"INVALID_SAMPLE");
                    else if(discontinuity) inc(counts,"DISCONTINUITY");
                    else if((ns >= (long)field(d,"warmUntil") && ns >= (long)field(d,"cooldownUntil")) || hit) {
                        if(before==0 && previous!=null && ns-firstAt>650_000_000L) inc(counts,"FIRST_EXPIRED");
                        if(before==1 && after==0) {
                            if(elapsed>160_000_000L) inc(counts,"ACCELERATION_TIMEOUT");
                            else if(elapsed<40_000_000L) inc(counts,"REVERSAL_TOO_EARLY");
                            else inc(counts,"ACCELERATION_SUPPORT_SHORT");
                        }
                        if(before==2 && after!=2) {
                            if(elapsed>180_000_000L) inc(counts,"BRAKING_TIMEOUT");
                            else if(support<40_000_000L) inc(counts,"BRAKING_SUPPORT_SHORT");
                            else if(hit) inc(counts,"RECOGNIZED");
                            else if(previous==null) inc(counts,"FIRST_STROKE");
                            else {
                                double alignment=0;for(int i=0;i<3;i++)alignment+=previous[i]*dir[i];
                                if(alignment<0.80)inc(counts,"PAIR_DIRECTION");
                                if(began-firstAt<150_000_000L||began-firstAt>600_000_000L)inc(counts,"PAIR_INTERVAL");
                            }
                        }
                    }
                }
                if (detail && legacy && (before!=after || hit))
                    System.out.printf(Locale.ROOT,"s=%d t=%.6f phase=%d->%d elapsed=%.3f support_before=%.3f hit=%s%n",sensitivity,ms,before,after,(ns-at)/1e6,support/1e6,hit);
            }
            System.out.println("sensitivity="+sensitivity+" samples="+rows.size()+" recognized="+hits.size()+" hit_relative_ms="+hits+" admissionRejected="+admissionRejected+" freshnessDisagreements="+mismatches+" causes="+counts);
        }
        System.out.println("mode="+mode+" causes="+(legacy?"INFERRED_0.2_GUARDS":"PRODUCTION_EVENTS_AGGREGATED_3_SCALES")+"; cold filter, missing prehistory, no accuracy denominator.");
    }
}
