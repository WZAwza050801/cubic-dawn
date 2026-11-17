import ctypes as c, time, subprocess
from pathlib import Path
x=c.CDLL('libX11.so.6'); t=c.CDLL('libXtst.so.6')
x.XOpenDisplay.argtypes=[c.c_char_p]; x.XOpenDisplay.restype=c.c_void_p
x.XStringToKeysym.argtypes=[c.c_char_p]; x.XStringToKeysym.restype=c.c_ulong
x.XKeysymToKeycode.argtypes=[c.c_void_p,c.c_ulong];x.XKeysymToKeycode.restype=c.c_uint
x.XSetInputFocus.argtypes=[c.c_void_p,c.c_ulong,c.c_int,c.c_ulong]
x.XFlush.argtypes=[c.c_void_p]
t.XTestFakeKeyEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeButtonEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeMotionEvent.argtypes=[c.c_void_p,c.c_int,c.c_int,c.c_int,c.c_ulong]
d=x.XOpenDisplay(b':98'); assert d
window=int(subprocess.check_output(['xwininfo','-display',':98','-root','-tree'],text=True).split('"Cubic Dawn"')[0].split()[-1],16)
x.XSetInputFocus(d,window,1,0);x.XFlush(d)
def key(name,hold=.20):
 code=x.XKeysymToKeycode(d,x.XStringToKeysym(name.encode()))
 t.XTestFakeKeyEvent(d,code,1,0); x.XFlush(d);time.sleep(hold)
 t.XTestFakeKeyEvent(d,code,0,0); x.XFlush(d);time.sleep(.25)
def click(button=1):
 t.XTestFakeButtonEvent(d,button,1,0);x.XFlush(d);time.sleep(.2)
 t.XTestFakeButtonEvent(d,button,0,0);x.XFlush(d);time.sleep(.3)
def shot(name):
 output=Path('build/ui-smoke');output.mkdir(parents=True,exist_ok=True)
 subprocess.run(['import','-display',':98','-window','root',str(output/(name+'.png'))],check=True)
def items():
 return {int(a[1]):tuple(map(int,a[2:])) for a in (l.split() for l in Path('build/ore-upgrade-smoke.txt').read_text().splitlines()) if a[0]=='item'}


def box(kind):
 for line in Path('build/ore-upgrade-smoke.txt').read_text().splitlines():
  p=line.split()
  if p[0]=='box' and int(p[4])==kind:
   return dict(burn=int(p[5]),progress=int(p[6]),slots=[tuple(map(int,s.split(':'))) for s in p[7:]])
 raise AssertionError('missing container')
def choose(index):
 t.XTestFakeMotionEvent(d,0,135+index%9*36,169+index//9*40,0);x.XFlush(d);click()


key('Escape');key('o')
text=Path('build/ore-upgrade-smoke.txt').read_text()
blocks=[l.split() for l in text.splitlines() if len(l.split())==4 and l.split()[0].lstrip('-').isdigit()]
assert len(blocks)==1806 and any(p[3]=='18' for p in blocks) and any(p[3]=='19' for p in blocks)
revision=next(l for l in text.splitlines() if l.startswith('revision '))
key('o');key('F5');assert revision in Path('build/ore-upgrade-smoke.txt').read_text()
shot('resource-upgrade');key('q')
print('PASS UI_EXPLICIT_ORE_UPGRADE NO_REPEAT NO_BLOCK_COUNT_CHANGE')
