"""Store private credentials outside the published source; never print values."""
import argparse, getpass, os, secrets, subprocess
from pathlib import Path
from dotenv import dotenv_values
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--voice-only',action='store_true')
args=parser.parse_args()
state=Path.home()/'.config/poetry-companion'
voice=state/'voice'
voice.mkdir(parents=True,exist_ok=True)
if os.name=='nt':
    sid=subprocess.check_output(['whoami','/user','/fo','csv','/nh'],text=True).strip().split(',')[-1].strip('"')
    subprocess.run(['icacls',str(state),'/inheritance:r','/grant:r',f'*{sid}:(OI)(CI)F','*S-1-5-18:(OI)(CI)F'],check=True,capture_output=True)
else:
    state.chmod(0o700)
    voice.chmod(0o700)
env=voice/'service.env'
if not env.exists():
    env.write_text('API_KEY='+secrets.token_hex(32)+'\n',encoding='utf-8')
token=dotenv_values(env).get('API_KEY')
if not token: raise SystemExit('Missing voice token in private service.env')
properties=state/'backend.properties'
original=properties.read_text(encoding='utf-8') if properties.exists() else ''
entries={'ai.companion.voice.base-url':'http://127.0.0.1:5050','ai.companion.voice.api-key':token}
if not args.voice_only:
    key=os.environ.get('DEEPSEEK_API_KEY') or getpass.getpass('DeepSeek API key (hidden): ').strip()
    if not key or any(c.isspace() for c in key): raise SystemExit('A nonempty key without whitespace is required')
    entries['ai.deepseek.api-key']=key
lines=[line for line in original.splitlines() if line.partition('=')[0].strip() not in entries]
lines += [f'{name}={value}' for name,value in entries.items()]
properties.write_text('\n'.join(lines)+'\n',encoding='utf-8')
if os.name!='nt':
    env.chmod(0o600)
    properties.chmod(0o600)
print('Private configuration saved outside the repository. Restart the backend.')
