package ubicomp.steptracker;

import java.util.ArrayList;

public class Filter {

    private double[] alpha_low_0_hz = {1.0, -1.979133761292768, 0.979521463540373};
    private double[] beta_low_0_hz = {0.000086384997973502, 0.000172769995947004, 0.000086384997973502};

    private double[] alpha_low_5_hz = {1.0, -1.80898117793047, 0.827224480562408};
    private double[] beta_low_5_hz = {0.095465967120306, -0.172688631608676, 0.095465967120306};

    private double[] alpha_high_1_hz = {1.0, -1.905384612118461, 0.910092542787947};
    private double[] beta_high_1_hz = {0.953986986993339, -1.907503180919730, 0.953986986993339};


    public Double filterLowPass0Hz(ArrayList<Double> data, ArrayList<Double> initialData) {
        return filter(data, initialData, alpha_low_0_hz, beta_low_0_hz);
    }

    public Double filterLowPass5Hz(ArrayList<Double> data, ArrayList<Double> initialData) {
        return filter(data, initialData, alpha_low_5_hz, beta_low_5_hz);
    }

    public Double filterHighPass1Hz(ArrayList<Double> data, ArrayList<Double> initialData) {
        return filter(data, initialData, alpha_high_1_hz, beta_high_1_hz);
    }

    private Double filter(ArrayList<Double> data, ArrayList<Double> initialData, double[] alpha, double[] beta) {

        int i = 2;
        Double filteredValue = alpha[0] * (
                    data.get(i)             * beta[0]  +
                    data.get(i-1)           * beta[1]  +
                    data.get(i-2)           * beta[2]  -
                    initialData.get(i-1)   * alpha[1]  -
                    initialData.get(i-2)   * alpha[2]);

        return filteredValue;
    }

    public Double dotProduct(Double xu, Double xg, Double yu, Double yg, Double zu, Double zg) {
        return (xu * xg) + (yu * yg) + (zu *zg);
    }
}
