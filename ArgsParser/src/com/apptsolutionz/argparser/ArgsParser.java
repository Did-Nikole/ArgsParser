package com.apptsolutionz.argparser;

import java.util.*;

/**
 * A robust command line argument parser that allows defining argument specifications
 * and returns a Map of parsed values.
 */
public class ArgsParser {

    // Map to store specifications, indexed by both short and long flags for quick lookup
    private final Map<String, ArgSpec> specMap = new HashMap<>();
    
    // Set to store required argument names (canonical names) for final validation
    private final Set<String> requiredArgs = new HashSet<>();
    
    // Stores the successful parse results
    private Map<String, Object> parsedResults = new HashMap<>(); 

    /**
     * Registers a new argument specification with the parser.
     * @param spec The ArgSpec object defining the argument.
     */
    public void addArg(ArgSpec spec) {
        // Use accessor methods for consistency and visibility
        if (spec.flag() != null && !spec.flag().isEmpty()) {
            if (!spec.flag().startsWith("-") || spec.flag().startsWith("--")) {
                throw new IllegalArgumentException("Short flag must start with '-' and not '--': " + spec.flag());
            }
            specMap.put(spec.flag(), spec);
        }

        if (spec.longFlag() != null && !spec.longFlag().isEmpty()) {
            if (!spec.longFlag().startsWith("--")) {
                throw new IllegalArgumentException("Long flag must start with '--': " + spec.longFlag());
            }
            specMap.put(spec.longFlag(), spec);
        }

        if (spec.required()) {
            requiredArgs.add(spec.getCanonicalName());
        }
    }

    /**
     * Parses the command-line arguments based on the registered specifications.
     * Stores the results internally and returns them upon success.
     * @param args The array of command-line arguments (usually from main(String[] args)).
     * @return {@code true} if parsing was successful, {@code false} otherwise.
     */
    public boolean parse(String[] args) {
        // Clear previous results and reset found flags for a new parse operation
        parsedResults = new HashMap<>(); 
        Set<String> foundFlags = new HashSet<>(); // Tracks which canonical names were found in the input
        
        // 1. First Pass: Process flags and read values
        for (int i = 0; i < args.length; i++) {
            String token = args[i];

            if (token.startsWith("-")) {
                ArgSpec spec = specMap.get(token);

                if (spec == null) {
                    System.err.println("Error: Unknown flag encountered: " + token);
                    return false; // Failure
                }

                String canonicalName = spec.getCanonicalName();
                foundFlags.add(canonicalName);

                try {
                    // --- SWITCH FLAG LOGIC (length == 0) ---
                    if (spec.length() == 0) {
                        // The presence of the flag implies 'true'. No value is consumed.
                        // ArgSpec validation ensures type is BOOLEAN.
                        parsedResults.put(canonicalName, true);
                        continue; // Move to the next token
                    }
                    
                    // --- SINGLE VALUE LOGIC (length == 1) ---
                    else if (spec.length() == 1) {
                        // Check if the next token is missing or is another flag
                        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                            throw new IllegalArgumentException("Missing value for argument: " + token);
                        }
                        String valueStr = args[i + 1];
                        parsedResults.put(canonicalName, convertValue(valueStr, spec.type()));
                        i++; // Consume the value
                    } 
                    
                    // --- MULTI-VALUE LOGIC (length > 1) ---
                    else { 
                        List<Object> values = new ArrayList<>();
                        for (int j = 1; j <= spec.length(); j++) {
                            // Check if value is missing or if we encounter a new flag prematurely
                            if (i + j >= args.length || args[i + j].startsWith("-")) {
                                throw new IllegalArgumentException("Missing " + (spec.length() - j + 1) + 
                                                                   " values for argument: " + token);
                            }
                            values.add(convertValue(args[i + j], spec.type()));
                        }
                        parsedResults.put(canonicalName, values);
                        i += spec.length(); // Consume all values
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Parsing Error: " + e.getMessage());
                    // Important: Clear results on failure
                    parsedResults.clear();
                    return false; // Failure
                }
            }
        }
        
        // 2. Final Validation: Check for missing required arguments
        for (String requiredName : requiredArgs) {
            if (!foundFlags.contains(requiredName)) {
                // Determine the primary flag for a clear error message
                String flag = specMap.entrySet().stream()
                    .filter(entry -> entry.getValue().getCanonicalName().equals(requiredName))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(requiredName); 

                System.err.println("Error: Compulsory argument is missing: " + flag);
                // Important: Clear results on failure
                parsedResults.clear();
                return false; // Failure
            }
        }
        
        // 3. Apply Defaults for missing optional arguments
        for (ArgSpec spec : specMap.values()) {
            String canonicalName = spec.getCanonicalName();
            
            // Only apply a default if the argument was NOT found during parsing
            if (!parsedResults.containsKey(canonicalName)) {
                
                // For a length=0 switch, the default is always false if not present
                if (spec.length() == 0) {
                    parsedResults.put(canonicalName, false);
                    continue;
                }
                
                // Apply defined defaultValue for optional arguments with length >= 1
                if (!spec.required() && spec.defaultValue() != null) {
                    parsedResults.put(canonicalName, spec.defaultValue());
                }
            }
        }

        // Return true on success
        return true;
    }

    /**
     * Converts a string value to the specified ArgType.
     * @param valueStr The string to convert.
     * @param type The target type.
     * @return The converted object.
     * @throws IllegalArgumentException if conversion fails.
     */
    private Object convertValue(String valueStr, ArgType type) throws IllegalArgumentException {
        try {
            return switch (type) {
                case INTEGER -> Integer.parseInt(valueStr);
                case DOUBLE -> Double.parseDouble(valueStr);
                case BOOLEAN -> {
                    String lower = valueStr.toLowerCase();
                    if (lower.equals("true") || lower.equals("1")) yield true;
                    if (lower.equals("false") || lower.equals("0")) yield false;
                    throw new IllegalArgumentException("Invalid boolean value: " + valueStr);
                }
                case STRING -> valueStr;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value format. Expected " + type.name() + " for value: " + valueStr);
        }
    }
    
    // ----------------------------------------------------------------------------------
    // --- NEW: Help Functionality ------------------------------------------------------
    // ----------------------------------------------------------------------------------

    /**
     * Displays a formatted help message listing all registered arguments, their
     * required status, flags, type, and expected length.
     */
    public void showHelp() {
        System.out.println("--- Command Line Argument Help ---");
        
        // Use a TreeSet to ensure unique specs and sort them alphabetically by canonical name
        Set<ArgSpec> uniqueSpecs = new TreeSet<>(Comparator.comparing(ArgSpec::getCanonicalName));
        uniqueSpecs.addAll(specMap.values());

        // Find the maximum length of the flags string for alignment
        int maxFlagLength = uniqueSpecs.stream()
            .map(spec -> {
                String shortFlag = spec.flag() != null ? spec.flag() : "";
                String longFlag = spec.longFlag() != null ? spec.longFlag() : "";
                return (shortFlag.isEmpty() ? "" : shortFlag + " | ") + longFlag;
            })
            .mapToInt(String::length)
            .max()
            .orElse(20); // Default size if no args are present

        String format = "  %-" + (maxFlagLength + 4) + "s %-12s %s\n";

        for (ArgSpec spec : uniqueSpecs) {
            String shortFlag = spec.flag() != null && !spec.flag().isEmpty() ? spec.flag() : "";
            String longFlag = spec.longFlag() != null && !spec.longFlag().isEmpty() ? spec.longFlag() : "";
            
            // Format flags: [-d | --debug] or [--file] or [-f]
            String flags = shortFlag.isEmpty() ? longFlag : 
                           (longFlag.isEmpty() ? shortFlag : shortFlag + " | " + longFlag);
            
            // Format required status
            String status = spec.required() ? "(REQUIRED)" : "(Optional)";
            
            // Format type and length information
            String details;
            if (spec.length() == 0) {
                details = String.format("[Switch: %s]", spec.type());
            } else if (spec.length() == 1) {
                details = String.format("[Type: %s, Value Count: 1]", spec.type());
            } else {
                details = String.format("[Type: %s, Value Count: %d (List)]", spec.type(), spec.length());
            }
            
            System.out.printf(format, flags, status, details);
        }
        System.out.println("----------------------------------");
    }
    
    // --- CONVENIENCE ACCESSOR METHODS FOR LIBRARY USE ---

    /**
     * Retrieves a parsed String argument by its canonical name.
     * @param name The canonical name of the argument (e.g., "file" for "--file").
     * @return The parsed String value, or null if not found.
     */
    public String getString(String name) {
        return (String) parsedResults.get(name);
    }
    
    /**
     * Retrieves a parsed Integer argument by its canonical name.
     * @param name The canonical name of the argument (e.g., "count" for "--count").
     * @return The parsed Integer value, or null if not found.
     */
    public Integer getInteger(String name) {
        return (Integer) parsedResults.get(name);
    }

    /**
     * Retrieves a parsed Boolean argument by its canonical name.
     * This is ideal for switch flags (length=0).
     * @param name The canonical name of the argument (e.g., "verbose" for "--verbose").
     * @return The parsed Boolean value, or null if not found.
     */
    public Boolean getBoolean(String name) {
        return (Boolean) parsedResults.get(name);
    }

    /**
     * Retrieves a parsed Double argument by its canonical name.
     * @param name The canonical name of the argument (e.g., "rate" for "--rate").
     * @return The parsed Double value, or null if not found.
     */
    public Double getDouble(String name) {
        return (Double) parsedResults.get(name);
    }

    /**
     * Retrieves a parsed multi-value argument as a List.
     * @param name The canonical name of the argument.
     * @return The parsed List of values, or null if not found.
     */
    public List<?> getList(String name) {
        Object result = parsedResults.get(name);
        return (result instanceof List) ? (List<?>) result : null;
    }
}
