# CSS Class Naming Conventions

## NativeWind `className` Naming

MacroFlow uses NativeWind utility classes exclusively. When composing class strings, follow these naming principles to keep styling intent clear and maintainable.

## Rules

### Component + Function Specificity
When a class string is used on a single component for a single purpose, the variable or constant holding it must describe both the component and the functionality it serves.

```tsx
// Good — describes component (SummaryCard) and function (over-target warning text)
const summaryCardOverTargetText = "text-warning dark:text-warning-dark font-semibold text-sm";

// Good — describes component (MealEntryRow) and function (swipe action container)
const mealEntryRowSwipeContainer = "flex-row items-center bg-surface dark:bg-surface-dark px-4 py-3";

// Bad — too generic, gives no context
const warningText = "text-warning dark:text-warning-dark font-semibold text-sm";
const container = "flex-row items-center px-4 py-3";
```

### Shared / Reusable Classes
When a class string is genuinely reused across multiple components or features, a generic descriptive name is acceptable — but it must still describe the visual role, not just the CSS values.

```tsx
// Good — generic but describes the visual role clearly
const cardSurface = "bg-surface dark:bg-surface-dark rounded-md p-4 border border-black/5 dark:border-white/5";
const mutedLabel = "text-muted dark:text-muted-dark text-sm";
const sectionHeading = "text-text dark:text-text-dark text-lg font-semibold";

// Bad — describes the CSS, not the role
const bgWhiteDark = "bg-surface dark:bg-surface-dark";
const textSmGray = "text-muted dark:text-muted-dark text-sm";
```

### Inline Class Strings
For short, single-use class strings written inline (not extracted to a variable), add a comment on the same line if the intent is not immediately obvious from context.

```tsx
// Good — context is clear from surrounding JSX
<View className="flex-row items-center gap-4 px-4 py-3">

// Good — intent not obvious, comment added
<View className="min-h-[44px] min-w-[44px] justify-center"> {/* MacroProgressBar touch target wrapper */}

// Bad — no context, ambiguous
<View className="absolute bottom-0 left-0 right-0 p-4">
```

### Naming Pattern
For extracted class string constants, use camelCase with the pattern:

```
{componentName}{FeatureOrState}
```

Examples:
- `summaryCardCaloriesRemaining`
- `macroProgressBarOverTarget`
- `addMealScreenSearchField`
- `foodDetailPortionChipSelected`
- `mealEntryRowDeleteAction`

For shared/generic classes:
- `cardSurface`
- `primaryButton`
- `mutedLabel`
- `sectionHeading`
- `dividerLine`

## What This Does NOT Apply To
- NativeWind token names in `tailwind.config.js` — those follow the token naming convention in `nativewind.md`
- Test files — class names in tests can be minimal
