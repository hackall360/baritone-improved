/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.humanization;

import baritone.api.behavior.humanization.HumanizationProfileSnapshot;

/**
 * Online variance accumulator used to capture statistics about player actions.
 */
public final class RunningStat {

    private long samples;
    private double mean;
    private double m2;
    private double min;
    private double max;

    public RunningStat() {
        this.samples = 0L;
        this.mean = 0.0;
        this.m2 = 0.0;
        this.min = Double.NaN;
        this.max = Double.NaN;
    }

    public void add(double value) {
        this.samples++;
        double delta = value - this.mean;
        this.mean += delta / this.samples;
        double delta2 = value - this.mean;
        this.m2 += delta * delta2;
        if (Double.isNaN(this.min) || value < this.min) {
            this.min = value;
        }
        if (Double.isNaN(this.max) || value > this.max) {
            this.max = value;
        }
    }

    public void clear() {
        this.samples = 0L;
        this.mean = 0.0;
        this.m2 = 0.0;
        this.min = Double.NaN;
        this.max = Double.NaN;
    }

    public boolean hasSamples() {
        return this.samples > 0L;
    }

    public long getSamples() {
        return this.samples;
    }

    public double getMean() {
        return this.samples > 0 ? this.mean : 0.0;
    }

    public double getStdDev() {
        return this.samples > 1 ? Math.sqrt(this.m2 / (this.samples - 1)) : 0.0;
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public HumanizationProfileSnapshot.RunningStat snapshot() {
        return new HumanizationProfileSnapshot.RunningStat(
                this.samples,
                getMean(),
                getStdDev(),
                this.min,
                this.max
        );
    }
}
