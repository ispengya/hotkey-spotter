package com.ispengya.hotkey.common.exception;

import java.util.*;

/**
 * @author ispengya
 * @date 2026/1/18 14:18
 */
public class Main {

    public static void main(String[] args) {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(1);
        list.add(1);
        list.add(3);

        int[] arr = new int[]{1,5,4,2,9,9,9};

//        System.out.println(maxSum(list, 2, 2));
        System.out.println(maximumSubarraySum(arr, 3));
    }


    public static long maxSum(List<Integer> nums, int m, int k) {
        int len = nums.size();
        long mxSum = 0;
        Map<Integer,Integer> map = new HashMap<>();
        long sum = 0;
        for(int i=0;i<k;i++){
            int num = nums.get(i);
            sum+=num;
            map.put(num, map.getOrDefault(num,0)+1);
        }
        if(map.size()>=m){
            mxSum = sum;
        }

        for(int i=k;i<len;i++){
            sum-=nums.get(i-k);
            sum+=nums.get(i);

            map.put(nums.get(i-k), map.get(nums.get(i-k))-1);
            if(map.get(nums.get(i-k))==0){
                map.remove(nums.get(i-k));
            }
            map.put(nums.get(i), map.getOrDefault(nums.get(i), 0)+1);
            if(map.size()>=m){
                mxSum = Math.max(mxSum, sum);
            }
        }

        return mxSum;
    }

    public static long maximumSubarraySum(int[] nums, int k) {
        Map<Integer,Integer> map = new HashMap<>();
        int maxSum = 0;
        int sum = 0;
        for(int i=0;i<k;i++){
            int num = nums[i];
            sum+=num;
            map.put(num, map.getOrDefault(num, 0)+1);
        }
        if(map.size()==k){
            maxSum=sum;
        }

        for(int i=k;i<nums.length;i++){
            int num1 = nums[i-k];
            int num2 = nums[i];
            sum-=num1;
            sum+=num2;
            map.put(num1,map.get(num1)-1);
            if(map.get(num1)==0){
                map.remove(num1);
            }
            map.put(num2, map.getOrDefault(num2,0)+1);
            if(map.size()==k){
                maxSum = Math.max(maxSum,sum);
            }
        }

        return maxSum;
    }
}
