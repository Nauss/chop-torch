import fr.nauss.choptorch.ChopDetector;
import java.util.*;

/** Entirely SYNTHETIC, not time-warped phone data and not a physical success rate.
 * Two smooth acceleration/braking cycles at equal amplitude; moderate transverse
 * curvature vanishes at each axis crossing. Freshness is BYPASSED (detector only).
 * --require-pair is a red-capable single-case expectation, not a shipping gate.
 */
class SpeedSweep {
    static int pair(int period, double amplitude, int sensitivity, double[] axis, int[] cadence, double curve, Map<String,Integer> events) {
        ChopDetector d=new ChopDetector(sensitivity,e->events.merge(e.toString(),1,Integer::sum));
        long t=1_000_000_000L; int index=0,hits=0;
        long start=t+800_000_000L, end=start+period*2_000_000L;
        // Unit transverse vector perpendicular to the chosen axis.
        double[] transverse=Math.abs(axis[2])<0.9 ? new double[]{-axis[1],axis[0],0} : new double[]{1,0,0};
        double norm=Math.sqrt(Arrays.stream(transverse).map(x->x*x).sum());
        while(t<end+200_000_000L) {
            double phase=2*Math.PI*(t-start)/(period*1e6);
            double a=t>=start && t<end ? amplitude*Math.sin(phase) : 0;
            double b=t>=start && t<end ? curve*amplitude*Math.sin(phase)*Math.sin(phase) : 0;
            double[] v=new double[3];
            for(int k=0;k<3;k++)v[k]=axis[k]*a+transverse[k]/norm*b;
            if(d.sample(t,v[0],v[1],v[2]+9.81))hits++;
            t+=cadence[index++%cadence.length]*1_000_000L;
        }
        return hits;
    }
    public static void main(String[] args) {
        if(args.length==2 && args[0].equals("--require-pair")) {
            Map<String,Integer> events=new TreeMap<>();
            int period=Integer.parseInt(args[1]);
            int hits=pair(period,6,100,new double[]{1,0,0},new int[]{8},0,events);
            System.out.println("SYNTHETIC periodMs="+period+" amplitude=6 sensitivity=100 hits="+hits+" events="+events);
            if(hits!=1)throw new AssertionError("Expected one coherent pair at unchanged amplitude and sensitivity; not a labelled physical failure");
            return;
        }
        if(args.length!=0)throw new IllegalArgumentException("optional --require-pair PERIOD_MS");
        System.out.println("SYNTHETIC detector-only; no personal data, no physical intent labels");
        System.out.println("periodMs\tamplitude\tsensitivity\taxis\tcadence\tcurve\thits\tevents");
        double[][] axes={{1,0,0},{0,-1,0},{0,0,1},{-0.6,0,-0.8}};
        int[][] cadences={{7},{8,9,8,8,9},{8,17,9},{20}};
        for(int period:new int[]{100,120,140,160,200,280,400})
        for(double amplitude:new double[]{6,12,24})for(int s:new int[]{0,50,100})
        for(int a=0;a<axes.length;a++)for(int c=0;c<cadences.length;c++)for(double curve:new double[]{0,0.15}) {
            Map<String,Integer> events=new TreeMap<>();
            int hits=pair(period,amplitude,s,axes[a],cadences[c],curve,events);
            System.out.printf(Locale.ROOT,"%d\t%.1f\t%d\t%d\t%d\t%.2f\t%d\t%s%n",period,amplitude,s,a,c,curve,hits,events);
        }
    }
}
