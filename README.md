# weather-report

Dummy app for Bones testing.

## Development Mode


attach to a repl and evaluate the forms at the bottom of core.clj

```
  (-main nil "-use-fake-ldap") ; backend process
  ;;or 
  (-main "other/conf.edn")

```

### CSS development

    npm install
    sass -I node_modules --watch src/scss:resources/public/css

### Run application:

```
docker-compose up -d
lein clean
lein trampoline run
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

### Run tests:

```
lein clean
lein test                              # clj
lein doo phantom test once             # cljs
```

### Automated tests:

```
docker-compose up -d
lein trampoline run config/common.edn -use-fake-ldap
lein cljsbuild once min 
lein with-profile browser test
```

The above command assumes that you have [phantomjs](https://www.npmjs.com/package/phantomjs) installed. However, please note that [doo](https://github.com/bensu/doo) can be configured to run cljs.test in many other JS environments (chrome, ie, safari, opera, slimer, node, rhino, or nashorn).

## Production Build

```
lein clean
lein cljsbuild once min
lein uberjar
```
