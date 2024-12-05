# Capacity to Ship Packages Within D Days

## Problem Statement
You are given an array `weights` where `weights[i]` represents the weight of the `i`th package and an integer `days`. You need to find the minimum capacity of a ship that can deliver all packages within `days` days.

## Approach
The problem can be solved using **Binary Search on the Capacity of the Ship**. The idea is to find the smallest possible capacity such that all packages can be shipped within the given number of days.

### Key Insights:
1. The minimum capacity of the ship must be at least the weight of the heaviest package (`max(weights)`).
2. The maximum capacity of the ship must be the sum of all weights (`sum(weights)`), as this would allow shipping all packages in one day.
3. Using binary search, we can efficiently find the minimum feasible capacity.

## Algorithm
### Steps:
1. **Initialize Binary Search Bounds**:
   - `low = max(weights)`
   - `high = sum(weights)`
2. **Binary Search**:
   - Calculate `mid` as the average of `low` and `high`.
   - Check if `mid` is a feasible capacity using a helper function.
   - If feasible, update `high` to `mid`.
   - Otherwise, update `low` to `mid + 1`.
3. **Feasibility Check**:
   - Simulate the shipping process using the given capacity.
   - If the number of days required is less than or equal to `days`, the capacity is feasible.
4. Return `low` as the minimum feasible capacity.

## Code Implementation in Java
```java
import java.util.*;

public class ShipPackages {

    // Main function to find the minimum capacity
    public int shipWithinDays(int[] weights, int days) {
        int low = Arrays.stream(weights).max().getAsInt(); // Minimum capacity is the heaviest package
        int high = Arrays.stream(weights).sum();           // Maximum capacity is the sum of all weights

        while (low < high) {
            int mid = low + (high - low) / 2;
            if (canShip(weights, days, mid)) {
                high = mid; // Try for a smaller capacity
            } else {
                low = mid + 1; // Increase capacity
            }
        }

        return low;
    }

    // Helper function to check if a given capacity is feasible
    private boolean canShip(int[] weights, int days, int capacity) {
        int currentWeight = 0;
        int requiredDays = 1; // Start with one day

        for (int weight : weights) {
            if (currentWeight + weight > capacity) {
                requiredDays++; // Start a new day
                currentWeight = 0;
            }
            currentWeight += weight;
        }

        return requiredDays <= days;
    }

    // Main method for testing
    public static void main(String[] args) {
        ShipPackages sp = new ShipPackages();
        int[] weights = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int days = 5;
        System.out.println("Minimum capacity to ship packages in " + days + " days: " + sp.shipWithinDays(weights, days));
    }
}
```

## Code Explanation
### shipWithinDays
- Initializes the binary search bounds (`low` and `high`).
- Performs binary search to find the minimum feasible capacity.

### canShip
- Simulates the process of shipping packages using the given capacity.
- Counts the number of days required to ship all packages.
- Returns `true` if the number of days required is less than or equal to `days`.

### Example Execution
#### Input:
```java
weights = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
days = 5;
```
#### Output:
```java
Minimum capacity to ship packages in 5 days: 15
```
#### Explanation:
- With a capacity of `15`, the packages can be shipped as follows:
  - Day 1: 1, 2, 3, 4, 5
  - Day 2: 6, 7
  - Day 3: 8
  - Day 4: 9
  - Day 5: 10
