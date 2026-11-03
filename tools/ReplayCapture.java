import fr.nauss.choptorch.ChopDetector;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Local, explicitly supplied CSV: relative_ms,x,y,z (no header).
 * Uses the compiled production Kotlin detector. Never a translated algorithm.
 * Input/output may be personal: keep outside Git. No callback freshness inferred.
 */
class ReplayCapture {
    static Object field(ChopDetector d, String name) throws Exception {
        Field f = ChopDetector.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(d);
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("CSV path required; optional --detail");
        boolean detail = args.length == 2 && args[1].equals("--detail");
        List<String> rows = Files.readAllLines(Path.of(args[0]));
        for (int sensitivity : new int[]{0, 50, 100}) {
            ChopDetector d = new ChopDetector(sensitivity);
            List<Double> hits = new ArrayList<>();
            Map<String,Integer> transitions = new TreeMap<>();
            for (String row : rows) {
                String[] c = row.split(",");
                if(c.length != 4) throw new IllegalArgumentException("Expected four columns");
                double ms = Double.parseDouble(c[0]);
                long ns = 1_000_000_000L + Math.round(ms * 1e6);
                double[] raw = {Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3])};
                int before = (int)field(d,"phase");
                long phaseAt = (long)field(d,"phaseAt");
                double[] dir = ((double[])field(d,"direction")).clone();
                boolean hit = d.sample(ns,raw[0],raw[1],raw[2]);
                int after = (int)field(d,"phase");
                if (hit) hits.add(ms);
                if(before != after || hit) {
                    transitions.merge(before+"->"+after,1,Integer::sum);
                    if(detail) {
                        double[] g = (double[])field(d,"gravity");
                        double dot=0, mag=0;
                        for(int i=0;i<3;i++) { double v=raw[i]-g[i]; dot+=v*dir[i]; mag+=v*v; }
                        System.out.printf(Locale.ROOT,"s=%d t=%.3f phase=%d->%d elapsed=%.1f mag=%.2f dot=%.2f firstAt=%.1f hit=%s%n",sensitivity,ms,before,after,(ns-phaseAt)/1e6,Math.sqrt(mag),dot,((long)field(d,"firstAt")-1_000_000_000L)/1e6,hit);
                    }
                }
            }
            System.out.println("sensitivity="+sensitivity+" samples="+rows.size()+" recognized="+hits.size()+" hit_relative_ms="+hits+" transitions="+transitions);
        }
        System.out.println("Unannotated private replay: no accuracy denominator; SampleFreshness NOT replayed.");
    }
}
