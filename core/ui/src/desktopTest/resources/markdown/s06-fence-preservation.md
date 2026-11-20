# S06 Markdown fence fixture

This file is a harmless local fixture for the Markdown transformation tests.

A four-backtick fence keeps shorter fences and image examples literal:

````markdown
```html
<img src="assets/inside.png" alt="Inside [1]" />
```

![Inside screenshot](assets/inside.png)
````

Outside the fence the same examples are converted:

<img src="docs/outside.png" alt="Outside" />

![Outside screenshot](docs/outside.png)
