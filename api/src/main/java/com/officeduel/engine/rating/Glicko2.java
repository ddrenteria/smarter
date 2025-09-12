package com.officeduel.engine.rating;

public final class Glicko2 {
    // Default parameters per Product&GamePlan: r=1500, RD=350, vol=0.06
    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RD = 350.0;
    public static final double DEFAULT_VOLATILITY = 0.06;

    // Conversions
    private static final double SCALE = 173.7178; // q = ln(10)/400 implicit

    public record Rating(double r, double rd, double vol) {}

    private static double g(double phi) { return 1.0 / Math.sqrt(1.0 + 3.0 * (0.0057565) * phi * phi); }
    private static double E(double mu, double mu_j, double phi_j) { return 1.0 / (1.0 + Math.exp(-g(phi_j) * (mu - mu_j))); }

    // One-opponent update
    public static Rating update(Rating player, Rating opponent, double score) {
        double mu = (player.r - DEFAULT_RATING) / SCALE;
        double phi = player.rd / SCALE;
        double mu_j = (opponent.r - DEFAULT_RATING) / SCALE;
        double phi_j = opponent.rd / SCALE;
        double v = 1.0 / (g(phi_j) * g(phi_j) * E(mu, mu_j, phi_j) * (1 - E(mu, mu_j, phi_j)));
        double delta = v * g(phi_j) * (score - E(mu, mu_j, phi_j));
        double phiStar = Math.sqrt(phi * phi + player.vol * player.vol);
        double phiPrime = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double muPrime = mu + phiPrime * phiPrime * g(phi_j) * (score - E(mu, mu_j, phi_j));
        double rPrime = muPrime * SCALE + DEFAULT_RATING;
        double rdPrime = phiPrime * SCALE;
        return new Rating(rPrime, Math.max(50.0, rdPrime), player.vol); // apply RD floor 50
    }
}


