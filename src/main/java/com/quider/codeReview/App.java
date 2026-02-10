package com.quider.codeReview;

/**
 * Main application class
 */
public class App {
    
    public static void main(String[] args) {
        System.out.println("Hello, Code Review!");
        
        // Demonstrate using the Calculator utility
        Calculator calculator = new Calculator();
        int sum = calculator.add(5, 3);
        int difference = calculator.subtract(10, 4);
        int product = calculator.multiply(6, 7);
        int quotient = calculator.divide(20, 4);
        
        System.out.println("Calculator Demo:");
        System.out.println("5 + 3 = " + sum);
        System.out.println("10 - 4 = " + difference);
        System.out.println("6 * 7 = " + product);
        System.out.println("20 / 4 = " + quotient);
    }
}
