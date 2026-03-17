# NativeWind Styling Guide

## Setup

NativeWind wraps React Native primitives so `className` works like Tailwind CSS. The config lives in `tailwind.config.js` at the project root; Babel and Metro are configured to process it.

## Token Set (`tailwind.config.js`)

```js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./App.{js,jsx,ts,tsx}', './src/**/*.{js,jsx,ts,tsx}'],
  darkMode: 'class', // controlled by ThemeProvider in App.tsx — supports Light/Dark/System user preference
  theme: {
    extend: {
      colors: {
        background: { DEFAULT: '#FFFFFF', dark: '#0B0B0C' },
        surface:    { DEFAULT: '#F7F7F8', dark: '#141416' },
        text:       { DEFAULT: '#111827', dark: '#F3F4F6' },
        muted:      { DEFAULT: '#6B7280', dark: '#9CA3AF' },
        primary:    { DEFAULT: '#0EA5E9', dark: '#38BDF8' },
        warning:    { DEFAULT: '#F59E0B', dark: '#FBBF24' },  // over-target states only
      },
      borderRadius: { md: '12px' },
      spacing: {
        // explicit scale — use only these values
        // 2, 4, 6, 8, 12, 16, 20, 24 (maps to p-2, p-4, etc.)
      },
      fontSize: {
        // xs, sm, base, lg, xl, 2xl only
      },
    },
  },
  plugins: [],
};
```

## Rules

### Styling
- Use `className` on all RN primitives (`View`, `Text`, `Pressable`, `TextInput`, etc.)
- Never use `StyleSheet.create` or inline `style={{}}` props
- Keep class strings short and semantic — prefer `bg-surface` over `bg-[#F7F7F8]`
- Spacing: multiples of the defined scale only (`p-2`, `p-4`, `p-6`, `p-8`, `p-12`, `p-16`, `p-20`, `p-24`)
- Typography: `text-xs|sm|base|lg|xl|2xl` + `font-medium|semibold` only
- Layout: `flex`, `flex-row`, `gap-*`, `px-*`, `py-*`, `rounded-md`, `border`, `border-black/5 dark:border-white/5`
- Note: `muted` is a flat color token — do NOT use `border-muted/10` (opacity modifiers won't work). Use `border-black/5 dark:border-white/5` for subtle borders instead.

### Dark Mode
- `darkMode: 'class'` — the `dark` class is applied to the root view by `ThemeProvider` based on `userStore.theme` (`light` | `dark` | `system`); when `system`, `useColorScheme()` determines the active theme
- Always pair light and dark variants: `bg-surface dark:bg-surface-dark`
- Press states: `active:opacity-90`
- Disabled states: `disabled:opacity-50`
- Never hardcode hex colors in `className` — use token names

### Component Patterns

```tsx
// Card
<View className="bg-surface dark:bg-surface-dark rounded-md p-4 border border-black/5 dark:border-white/5">

// Primary button
<Pressable className="bg-primary dark:bg-primary-dark rounded-md px-4 py-3 active:opacity-90 disabled:opacity-50">
  <Text className="text-white font-medium text-base">Label</Text>
</Pressable>

// Ghost button
<Pressable className="rounded-md px-4 py-3 border border-black/10 dark:border-white/10 active:opacity-90">
  <Text className="text-text dark:text-text-dark font-medium text-base">Label</Text>
</Pressable>

// Pill / chip — unselected
<View className="bg-background dark:bg-background-dark px-3 py-1 rounded-full border border-black/5 dark:border-white/10">
  <Text className="text-text dark:text-text-dark font-medium text-sm">Label</Text>
</View>

// Pill / chip — selected
<View className="bg-primary dark:bg-primary-dark px-3 py-1 rounded-full border border-primary dark:border-primary-dark">
  <Text className="text-white font-medium text-sm">Label</Text>
</View>

// Over-target warning (SummaryCard / MacroProgressBar)
<Text className="text-warning dark:text-warning-dark font-semibold text-sm">+12g over</Text>

// Muted label
<Text className="text-muted dark:text-muted-dark text-sm">Hint text</Text>

// Section heading
<Text className="text-text dark:text-text-dark text-lg font-semibold">Heading</Text>
```

## `src/ui/` Primitives

All reusable primitives live in `src/ui/`. Each accepts a `className` prop for overrides.

| File | Component | Notes |
|---|---|---|
| `Button.tsx` | `Button` | `variant`: `primary` \| `ghost`; `disabled` prop |
| `Card.tsx` | `Card` | Surface background, rounded-md, border |
| `TextField.tsx` | `TextField` | Label + input + error message |
| `ListItem.tsx` | `ListItem` | Left icon/label, right value, optional chevron |
| `Chip.tsx` | `Chip` | Pill shape, selectable state |
| `Badge.tsx` | `Badge` | Small numeric or status indicator |
| `Divider.tsx` | `Divider` | `border-b border-black/5 dark:border-white/5` |
| `EmptyState.tsx` | `EmptyState` | Icon + heading + subtext + optional CTA |
| `Snackbar.tsx` | `Snackbar` | Bottom toast for undo / confirmation messages |

## Accessibility
- All `Pressable` elements must have `accessibilityRole` and `accessibilityLabel`
- Minimum touch target: wrap in a `View` with `className="min-h-[44px] min-w-[44px] justify-center"` if the visual element is smaller
- `TextInput` must have `accessibilityLabel` matching its visible label
