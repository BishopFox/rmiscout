package com.bishopfox.rmiscout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * From https://stackoverflow.com/questions/13157656/permutation-of-an-array-with-repetition-in-java
 */
public class Permutations {
    public static ArrayList<List<String>> permutations;

    private static void permute(String[] a, int k, PermuteCallback callback) {
        int n = a.length;

        int[] indexes = new int[k];
        int total = (int) Math.pow(n, k);

        String[] snapshot = new String[k];
        while (total-- > 0) {
            for (int i = 0; i < k; i++){
                snapshot[i] = a[indexes[i]];
            }
            callback.handle(snapshot);

            for (int i = 0; i < k; i++) {
                if (indexes[i] >= n - 1) {
                    indexes[i] = 0;
                } else {
                    indexes[i]++;
                    break;
                }
            }
        }
    }

    public static interface PermuteCallback{
        public void handle(String[] snapshot);
    };

    public static ArrayList<List<String>> permute(String[] chars, int length) {
        PermuteCallback callback = new PermuteCallback() {

            @Override
            public void handle(String[] snapshot) {
                permutations.add(Arrays.asList(snapshot.clone()));
            }
        };
        permutations = new ArrayList<List<String>>();
        permute(chars, length, callback);

        return permutations;
    }

}